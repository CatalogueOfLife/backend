package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.dao.*;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.UUID;

import javax.validation.Validator;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class XRelease extends ProjectRelease {
  private final int baseReleaseKey;
  private final SectorImportDao siDao;
  private List<Sector> sectors;
  private final User fullUser = new User();
  private final SyncFactory syncFactory;
  private final Int2IntMap sectorPriorities = new Int2IntOpenHashMap(); // sector keys
  private Taxon incertae;

  XRelease(SqlSessionFactory factory, SyncFactory syncFactory, NameUsageIndexService indexService, DatasetDao dDao, DatasetImportDao diDao, SectorImportDao siDao, NameDao nDao, SectorDao sDao,
           ImageService imageService,
           int releaseKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
           DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super("releasing extended", factory, indexService, diDao, dDao, nDao, sDao, imageService, DatasetInfoCache.CACHE.info(releaseKey, DatasetOrigin.RELEASE).sourceKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
    this.siDao = siDao;
    this.syncFactory = syncFactory;
    baseReleaseKey = releaseKey;
    fullUser.setKey(userKey);
    LOG.info("Build extended release for project {} from public release {}", datasetKey, baseReleaseKey);
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    d.setOrigin(DatasetOrigin.XRELEASE);
    if (ds.has(Setting.XRELEASE_ALIAS_TEMPLATE)) {
      String alias = CitationUtils.fromTemplate(d, ds.getString(Setting.XRELEASE_ALIAS_TEMPLATE));
      d.setAlias(alias);
    }
  }

  @Override
  void prepWork() throws Exception {
    createReleaseDOI();
    int newSectors = sDao.createMissingMergeSectorsForProject(datasetKey, fullUser.getKey());
    LOG.info("Created {} newly published merge sectors", newSectors);

    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sectors = sm.listByPriority(datasetKey, Sector.Mode.MERGE);
    }
  }

  @Override
  protected Integer createReleaseDOI() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      // find previous public release needed for DOI management
      final Integer prevReleaseKey = session.getMapper(DatasetMapper.class).previousRelease(newDatasetKey);
      return createReleaseDOI(prevReleaseKey);
    }
  }

  @Override
  void finalWork() throws Exception {
    incertae = createIncertaeSedisRoot();
    mergeSectors();

    updateState(ImportState.PROCESSING);
    // detect and group basionyms
    new HomotypicConsolidator(factory, newDatasetKey, sectorPriorities).groupAll();

    // flagging of suspicous usages
    resolveParentMismatches();
    resolveEmptyGenera();
    cleanImplicitTaxa();
    resolveDuplicateAcceptedNames();

    // create missing autonyms
    manageAutonyms();

    updateState(ImportState.ANALYZING);
    // update sector metrics. The entire releases metrics are done later by the superclass
    buildSectorMetrics();
    // finally also call the shared part
    super.finalWork();
  }

  /**
   * We copy the tables of the base release here, not the project
   */
  @Override
  <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session) {
    int count = session.getMapper(mapperClass).copyDataset(baseReleaseKey, newDatasetKey, false);
    LOG.info("Copied {} {}s from {} to {}", count, entity.getSimpleName(), baseReleaseKey, newDatasetKey);
  }

  /**
   * This updates the merge sector metrics with the final counts.
   * We do this at the very end as homotypic grouping and other final changes have impact on the sectors.
   */
  private void buildSectorMetrics() {
    // sector metrics
    for (Sector s : sectors) {
      var sim = siDao.getAttempt(s, s.getSyncAttempt());
      LOG.info("Build metrics for sector {}", s);
      siDao.updateMetrics(sim, newDatasetKey);
    }
  }

  private Taxon createIncertaeSedisRoot() {
    //TODO: add project setting that allows to reuse an existing incertae sedis root
    Name n = Name.newBuilder()
                 .datasetKey(newDatasetKey)
                 .id(UUID.randomUUID().toString())
                 .origin(Origin.OTHER)
                 .type(NameType.PLACEHOLDER)
                 .scientificName("Incertae Sedis")
                 .rank(Rank.KINGDOM)
                 .nomStatus(NomStatus.NOT_ESTABLISHED)
                 .createdBy(user)
                 .modifiedBy(user)
                 .build();
    Taxon t = new Taxon(n);
    t.setDatasetKey(newDatasetKey);
    t.setId(UUID.randomUUID().toString());
    t.setStatus(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    t.setOrigin(Origin.OTHER);
    t.setCreatedBy(user);
    t.setModifiedBy(user);

    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(NameMapper.class).create(n);
      session.getMapper(TaxonMapper.class).create(t);
    }
    return t;
  }

  /**
   * We do all extended work here, e.g. sector merging
   */
  private void mergeSectors() throws Exception {
    updateState(ImportState.INSERTING);
    int priority = 0;
    for (Sector s : sectors) {
      // the sector might not have been copied to the xrelease yet - we only copied all sectors from the base release, not the project.
      // create only if missing
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        if (sm.get(DSID.of(newDatasetKey, s.getId())) == null) {
          Sector s2 = new Sector(s);
          s2.setDatasetKey(newDatasetKey);
          sm.createWithID(s2);
        }
      }
      priority = s.getPriority() == null ? priority + 1 : s.getPriority();
      sectorPriorities.put((int)s.getId(), priority);
      checkIfCancelled();
      var ss = syncFactory.release(s, newDatasetKey, incertae, fullUser);
      ss.run();
      if (ss.getState().getState() != ImportState.FINISHED){
        throw new IllegalStateException("SectorSync failed with error: " + ss.getState().getError());
      }
      // copy sync attempt to local instances as it finished successfully
      s.setSyncAttempt(ss.getState().getAttempt());
    }
  }

  private void manageAutonyms() {
    LOG.info("Manage autonyms - not implemented");
  }

  private void cleanImplicitTaxa() {
    LOG.info("Clean implicit taxa - not implemented");
  }

  /**
   * Goes through all accepted species and infraspecies and makes sure the name matches the genus, species classification.
   * For example an accepted species Picea alba with a parent genus of Abies is taxonomic nonsense.
   * Badly classified names are assigned the doubtful status and an NameUsageIssue.NAME_PARENT_MISMATCH is flagged
   */
  private void resolveParentMismatches() {
    LOG.info("Resolve names with implicit parent mismatches");
  }

  /**
   * Changes empty genera to provisionally accepted or removes them completely if they have an ignorable origin
   */
  private void resolveEmptyGenera() {
    LOG.info("Resolve empty genera");
  }

  private void resolveDuplicateAcceptedNames() {
    LOG.info("Resolve duplicate accepted names");
  }

}
