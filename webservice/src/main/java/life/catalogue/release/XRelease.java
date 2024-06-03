package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSync;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.assembly.TreeMergeHandlerConfig;
import life.catalogue.basgroup.HomotypicConsolidator;
import life.catalogue.basgroup.SectorPriority;
import life.catalogue.common.date.DateUtils;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.dao.*;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.RematchMissing;
import life.catalogue.matching.UsageMatcherGlobal;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import javax.validation.Validator;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public class XRelease extends ProjectRelease {
  private static final Logger LOG = LoggerFactory.getLogger(XRelease.class);
  private final int baseReleaseKey;
  private final SectorImportDao siDao;
  private List<Sector> sectors;
  private final User fullUser = new User();
  private final SyncFactory syncFactory;
  private final UsageMatcherGlobal matcher;
  private final NameIndex ni;
  private XReleaseConfig xCfg;
  private TreeMergeHandlerConfig mergeCfg;
  private XIdProvider usageIdGen;
  private int failedSyncs;

  XRelease(SqlSessionFactory factory, SyncFactory syncFactory, UsageMatcherGlobal matcher, NameUsageIndexService indexService, ImageService imageService,
           DatasetDao dDao, DatasetImportDao diDao, SectorImportDao siDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
           int releaseKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
           DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super("releasing extended", factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, DatasetInfoCache.CACHE.info(releaseKey, DatasetOrigin.RELEASE).sourceKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
    this.siDao = siDao;
    this.syncFactory = syncFactory;
    this.matcher = matcher;
    this.ni = matcher.getNameIndex();
    baseReleaseKey = releaseKey;
    fullUser.setKey(userKey);
    LOG.info("Build extended release for project {} from public release {}", datasetKey, baseReleaseKey);
  }

  @VisibleForTesting
  void setCfg(XReleaseConfig xCfg) {
    this.xCfg = xCfg;
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    if (xCfg == null) {
      xCfg = loadConfig(ds.getURI(Setting.XRELEASE_CONFIG));
    }
    d.setOrigin(DatasetOrigin.XRELEASE);
    if (xCfg.alias != null) {
      String alias = CitationUtils.fromTemplate(d, xCfg.alias);
      d.setAlias(alias);
    }
    if (xCfg.title != null) {
      String title = CitationUtils.fromTemplate(d, xCfg.title);
      d.setTitle(title);
    }
    if (xCfg.version != null) {
      String version = CitationUtils.fromTemplate(d, xCfg.version);
      d.setVersion(version);
    }
    if (xCfg.description != null) {
      String description = CitationUtils.fromTemplate(d, xCfg.description);
      d.setDescription(description);
    }
  }

  @Override
  void prepWork() throws Exception {
    // fail early if components are not ready
    syncFactory.assertComponentsOnline();
    // ... or licenses of existing sectors are not compatible
    dataset = loadDataset(factory, datasetKey);
    final License projectLicense = dataset.getLicense();
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var sm = session.getMapper(SectorMapper.class);
      Set<Integer> sourceKeys = new HashSet<>();
      for (var s : sm.listByPriority(datasetKey, Sector.Mode.MERGE)) {
        if (!sourceKeys.contains(s.getSubjectDatasetKey())) {
          Dataset src = dm.get(s.getSubjectDatasetKey());
          if (!License.isCompatible(src.getLicense(), projectLicense)) {
            LOG.warn("License {} of project {} is not compatible with license {} of source {}: {}", projectLicense, datasetKey, src.getLicense(), src.getKey(), src.getTitle());
            throw new IllegalArgumentException("Source license " +src.getLicense()+ " of " + s + " is not compatible with license " +projectLicense+ " of project " + datasetKey);
          }
          sourceKeys.add(s.getSubjectDatasetKey());
        }
      }
    }

    // make sure the base release is fully matched
    // runs in parallel to the rest of the prep phase below
    Runnable matchMissingTask = new RematchMissing(factory, ni, null, baseReleaseKey);
    final var thread = ExecutorUtils.runInNewThread(matchMissingTask);

    try (SqlSession session = factory.openSession(true)) {
      var pm = session.getMapper(PublisherMapper.class);
      var publisher = pm.listAll(datasetKey);
      // create missing sectors in project from publishers for compatible licenses only
      for (var p : publisher) {
        int newSectors = sDao.createMissingMergeSectorsFromPublisher(datasetKey, fullUser.getKey(), p.getId(), xCfg.sourceDatasetExclusion);
        LOG.info("Created {} newly published merge sectors in project {} from publisher {} {}", newSectors, datasetKey, p.getAlias(), p.getId());
      }
    }

    // load all merge sectors from project as they not exist in the base release
    // note that target taxa still refer to temp identifiers used in the project, not the stable ids from the base release
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sectors = sm.listByPriority(datasetKey, Sector.Mode.MERGE);
      // make sure dataset still exists for MERGE sectors
      var iter = sectors.iterator();
      while(iter.hasNext()){
        var s = iter.next();
        if (s.getMode() == Sector.Mode.MERGE) {
          var src = DatasetInfoCache.CACHE.info(s.getSubjectDatasetKey(), true);
          if (src.deleted) {
            // remove sector
            LOG.warn("Removed merge sector {} as underlying dataset {} was deleted", s, src.key);
            sm.delete(s);
            iter.remove();
          }
        }
      }
      // match targets to base release
      for (var s : sectors) {
        if (s.getTarget() != null){
          s.getTarget().setStatus(TaxonomicStatus.ACCEPTED);
          NameUsageBase nu = new Taxon(s.getTarget());
          var m = matcher.match(baseReleaseKey, nu, null, true, false);
          if (m.isMatch()) {
            s.getTarget().setBroken(false);
            s.getTarget().setId(m.getId());
          } else {
            LOG.warn("Failed to match target {} of sector {}[{}] to base release {}. Ignoring sector target in release {}!", s.getTarget(), s.getId(), s.getSubjectDatasetKey(), baseReleaseKey, newDatasetKey);
            s.setTarget(null);
          }
        }
      }
    }
    createReleaseDOI();

    // setup id generator
    usageIdGen = new XIdProvider(datasetKey, attempt, newDatasetKey, cfg.release, ni, factory);

    // make sure the missing matching is completed before we deal with the real data
    thread.join();
  }

  @VisibleForTesting
  protected static XReleaseConfig loadConfig(URI url) {
    if (url == null) {
      LOG.warn("No XRelease config supplied, use defaults");
      return new XReleaseConfig();
    } else {
      try (InputStream in = url.toURL().openStream()) {
        // odd workaround to use the stream directly - which breaks the yaml parsing for some reason
        String yaml = InputStreamUtils.readEntireStream(in);
        return YamlUtils.readString(XReleaseConfig.class, yaml);
      } catch (IOException e) {
        throw new IllegalArgumentException("Invalid xrelease configuration at "+ url, e);
      }
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
    usageIdGen.removeIdsFromDataset(newDatasetKey);

    mergeSectors();

    updateState(ImportState.PROCESSING);
    // detect and group basionyms
    if (xCfg.groupBasionyms) {
      final LocalDateTime start = LocalDateTime.now();
      final var prios = new SectorPriority(getDatasetKey(), factory);
      var hc = HomotypicConsolidator.entireDataset(factory, newDatasetKey, prios::priority);
      if (xCfg.basionymExclusions != null) {
        hc.setBasionymExclusions(xCfg.basionymExclusions);
      }
      hc.consolidate();
      DateUtils.logDuration(LOG, hc.getClass(), start);

    } else {
      LOG.warn("Homotypic grouping disabled in xrelease configs");
    }

    // flagging of suspicous usages
    validateAndCleanTree();
    cleanImplicitTaxa();
    resolveDuplicateAcceptedNames();

    // remove orphan names and references
    removeOrphans(newDatasetKey);

    updateState(ImportState.ANALYZING);
    // flag loops and nonexisting parents
    flagLoops();
    // update sector metrics. The entire releases metrics are done later by the superclass
    buildSectorMetrics();
    // update metadata
    updateMetadata();
    // write id reports
    usageIdGen.report();
    // finally also call the shared part which e.g. archives metadata and creates source dataset records
    super.finalWork();
  }

  private void updateMetadata() {
    newDataset.appendNotes(String.format("Base release %s.", baseReleaseKey));
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetMapper.class).update(newDataset);
    }
  }

  /**
   * flag loops, synonyms pointing to synonyms and nonexisting parents
   */
  private void flagLoops() {
    // any chained synonyms?
    try (SqlSession session = factory.openSession(true)) {
      var chains = session.getMapper(NameUsageMapper.class).detectChainedSynonyms(newDatasetKey);
      if (chains != null && !chains.isEmpty()) {
        LOG.error("{} chained synonyms found in XRelease {}", chains.size(),newDatasetKey);

        var num = session.getMapper(NameUsageMapper.class);
        var vsm = session.getMapper(VerbatimSourceMapper.class);
        var key = DSID.<String>root(newDatasetKey);

        for (var id : chains) {
          key.id(id);
          vsm.addIssue(key, Issue.CHAINED_SYNONYM);
          var syn = num.getSimple(key);
          num.updateParentId(key, syn.getParentId(), user);
        }
      }
    }

    // any accepted names below synonyms? Move to accepted
    try (SqlSession session = factory.openSession(true)) {
      var synParents = session.getMapper(NameUsageMapper.class).detectParentSynoynms(newDatasetKey);
      if (synParents != null && !synParents.isEmpty()) {
        LOG.error("{} taxa found in XRelease {} with synonyms as their parent", synParents.size(),newDatasetKey);
        var num = session.getMapper(NameUsageMapper.class);
        var vsm = session.getMapper(VerbatimSourceMapper.class);

        var key = DSID.<String>root(newDatasetKey);
        for (var id : synParents) {
          key.id(id);
          vsm.addIssue(key, Issue.SYNONYM_PARENT);
          var syn = num.getSimpleParent(key);
          num.updateParentId(key, syn.getParentId(), user);
        }
      }
    }

    // cut potential cycles in the tree?
    try (SqlSession session = factory.openSession(true)) {
      var cycles = session.getMapper(NameUsageMapper.class).detectLoop(newDatasetKey);
      if (cycles != null && !cycles.isEmpty()) {
        LOG.error("{} cycles found in the parent-child classification of dataset {}", cycles.size(),newDatasetKey);
        var tm = session.getMapper(TaxonMapper.class);
        var num = session.getMapper(NameUsageMapper.class);
        var vsm = session.getMapper(VerbatimSourceMapper.class);

        Name n = Name.newBuilder()
                     .id("cycleParentPlaceholder")
                     .datasetKey(newDatasetKey)
                     .scientificName("Cycle parent holder")
                     .rank(Rank.UNRANKED)
                     .type(NameType.PLACEHOLDER)
                     .origin(Origin.OTHER)
                     .build();
        n.applyUser(user);
        Taxon cycleParent = new Taxon(n);
        cycleParent.setId("cycleParentPlaceholder");
        cycleParent.setParentId(mergeCfg.incertae.getId());
        tm.create(cycleParent);

        final DSID<String> key = DSID.root(newDatasetKey);
        for (String id : cycles) {
          vsm.addIssue(key.id(id), Issue.PARENT_CYCLE);
          num.updateParentId(key, cycleParent.getId(), user);
        }
        LOG.warn("Resolved {} cycles found in the parent-child classification of dataset {}", cycles.size(), newDatasetKey);
      }

    } catch (PersistenceException e) {
      // detectLoop is known to sometimes throw PSQLException: ERROR: temporary file size exceeds temp_file_limit
      LOG.warn("Failed to detect tree cycles in the parent-child classification of dataset {}", newDatasetKey, e);
    }

    // look for non existing parents
    try (SqlSession session = factory.openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var vsm = session.getMapper(VerbatimSourceMapper.class);
      var missing = num.listMissingParentIds(newDatasetKey);
      if (missing != null && !missing.isEmpty()) {
        LOG.error("{} usages found with a non existing parentID", missing.size());
        final String parent;
        if (mergeCfg.hasIncertae()) {
          parent = mergeCfg.incertae.getId();
        } else {
          parent = null;
        }
        final DSID<String> key = DSID.root(newDatasetKey);
        for (String id : missing) {
          vsm.addIssue(key.id(id), Issue.PARENT_ID_INVALID);
          num.updateParentId(key, parent, user);
        }
        LOG.warn("Resolved {} usages with a non existing parent in dataset {}", missing.size(),newDatasetKey);
      }
    }
  }

  /**
   * We copy the tables of the base release here, not the project
   */
  @Override
  <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session) {
    // we copy some entities from the project, not the base release though
    if (entity.equals(Publisher.class)) {
      super.copyTable(entity, mapperClass, session);

    } else {
      // copy all data from the base release
      int count = session.getMapper(mapperClass).copyDataset(baseReleaseKey, newDatasetKey, false);
      LOG.info("Copied {} {}s from {} to {}", count, entity.getSimpleName(), baseReleaseKey, newDatasetKey);

      // for editorial decisions and sectors we want all from the base release AND new ones for the merge sectors only from the project!!!
      if (entity.equals(Sector.class)) {
        session.commit();
        var sm = session.getMapper(SectorMapper.class);
        for (var s : sectors) {
          s.setDatasetKey(newDatasetKey);
          if (sm.exists(s)) {
            sm.update(s); // target is matched now and has changed
          } else {
            sm.createWithID(s);
          }
          // revert sectors as we might use the sectors as project sectors later on again, better don't alter them
          s.setDatasetKey(datasetKey);
        }
        session.commit();
      } else if (entity.equals(EditorialDecision.class)) {
        //TODO: copy merge decisions only...
      }
    }
  }

  /**
   * This updates the merge sector metrics with the final counts.
   * We do this at the very end as homotypic grouping and other final changes have impact on the sectors.
   */
  private void buildSectorMetrics() {
    final LocalDateTime start = LocalDateTime.now();
    // sector metrics
    for (Sector s : sectors) {
      if (s.getSyncAttempt() != null) {
        var sim = siDao.getAttempt(s, s.getSyncAttempt());
        LOG.info("Build metrics for sector {}", s);
        siDao.updateMetrics(sim, newDatasetKey);
      }
    }
    DateUtils.logDuration(LOG, "Building sector metrics", start);
  }

  @Override
  protected void onFinishLocked() throws Exception {
    // release id generator resources
    try {
      usageIdGen.close();
    } catch (Exception e) {
      LOG.error("Failed to close id generator", e);
    }
    super.onFinishLocked();
  }

  /**
   * We do all extended work here, e.g. sector merging
   */
  private void mergeSectors() throws Exception {
    final LocalDateTime start = LocalDateTime.now();
    // prepare merge handler config instance
    mergeCfg = new TreeMergeHandlerConfig(factory, xCfg, newDatasetKey, user);
    final int size = sectors.size();
    int counter = 0;
    failedSyncs = 0;
    // sequential id generators for extended records
    final Supplier<String> nameIdGen = new XIdGen();
    final Supplier<String> typeMaterialIdGen = new XIdGen();
    updateState(ImportState.INSERTING);
    for (Sector s : sectors) {
      LOG.info("Merge {}. #{} out of {}", s, counter++, size);
      // the sector might not have been copied to the xrelease yet - we only copied all sectors from the base release, not the project.
      // create only if missing
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        Sector sRel = sm.get(DSID.of(newDatasetKey, s.getId()));
        if (sRel == null) {
          sRel = new Sector(s);
          sRel.setDatasetKey(newDatasetKey);
          sm.createWithID(sRel);
        }
      }
      checkIfCancelled();
      SectorSync ss;
      try {
        ss = syncFactory.release(s, newDatasetKey, mergeCfg, nameIdGen, typeMaterialIdGen, usageIdGen, fullUser);
        ss.run();
        if (ss.getState().getState() != ImportState.FINISHED){
          failedSyncs++;
          LOG.error("Failed to sync {} with error: {}", s, ss.getState().getError());
        } else {
          // copy attempts to local instances as it finished successfully
          s.setSyncAttempt(ss.getState().getAttempt());
          // and also update our release copy!
          try (SqlSession session = factory.openSession(true)) {
            SectorMapper sm = session.getMapper(SectorMapper.class);
            sm.updateReleaseAttempts(DSID.of(datasetKey, s.getId()), newDatasetKey);
          }
        }
      } catch (NotFoundException e) {
        failedSyncs++;
        LOG.error("Sector {} was deleted. No sync possible", s);
        // remove from release
        try (SqlSession session = factory.openSession(true)) {
          SectorMapper sm = session.getMapper(SectorMapper.class);
          sm.delete(DSID.of(newDatasetKey, s.getId()));
        }
      }
    }

    LOG.info("All {} sectors merged, {} failed", counter, failedSyncs);
    DateUtils.logDuration(LOG, getClass(), start);
  }

  public int getFailedSyncs() {
    return failedSyncs;
  }
/**
   * Goes through all accepted infraspecies and checks if a matching autonym exists,
   * creating missing autonyms where needed.
   * An autonym is an infraspecific taxon that has the same species and infraspecific epithet.
   * We do this last to not persistent autonyms that we dont need after basionyms are grouped or status has changed for some other reason.
   */

  /**
   * Updates implicit names to be accepted (not doubtful) and removes implicit taxa with no children if configured to do so.
   */
  private void cleanImplicitTaxa() {
    LOG.warn("Clean implicit taxa - not implemented");
  }

  /**
   * Iterates over the entire tree of accepted names, validates taxa and resolves data.
   */
  private void validateAndCleanTree() {
    LOG.info("Clean and validate entire xrelease {}", newDatasetKey);
    final LocalDateTime start = LocalDateTime.now();
    try (SqlSession session = factory.openSession(true);
         var consumer = new TreeCleanerAndValidator(factory, newDatasetKey, xCfg.removeEmptyGenera)
    ) {
      var num = session.getMapper(NameUsageMapper.class);
      TreeTraversalParameter params = new TreeTraversalParameter();
      params.setDatasetKey(newDatasetKey);
      params.setSynonyms(false);

      PgUtils.consume(() -> num.processTreeLinneanUsage(params, true, false), consumer);
      metrics.setMaxClassificationDepth(consumer.getMaxDepth());
      LOG.info("{} usages out of {} flagged with issues during validation", consumer.getFlagged(), consumer.getCounter());

    } catch (Exception e) {
      LOG.error("Name validation & cleaning failed", e);
    }
    DateUtils.logDuration(LOG, TreeCleanerAndValidator.class, start);
  }

  private void resolveDuplicateAcceptedNames() {
    LOG.info("Resolve duplicate accepted names");
  }

}
