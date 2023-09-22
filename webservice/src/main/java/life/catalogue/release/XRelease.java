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
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.*;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.DatasetMatcher;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexImpl;
import life.catalogue.matching.UsageMatcherGlobal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.validation.Validator;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    if (xCfg.sourcePublisher != null) {
      // create missing sectors from publishers for compatible licenses only
      for (UUID pubKey : xCfg.sourcePublisher) {
        int newSectors = sDao.createMissingMergeSectorsFromPublisher(datasetKey, fullUser.getKey(), pubKey, xCfg.sourceDatasetExclusion);
        LOG.info("Created {} newly published merge sectors from publisher {}", newSectors, pubKey);
      }
    }

    // load all merge sectors from project as they not exist in the base release
    // note that target taxa still refer to temp identifiers used in the project, not the stable ids from the base release
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sectors = sm.listByPriority(datasetKey, Sector.Mode.MERGE);
      // match targets to base release
      for (var s : sectors) {
        if (s.getTarget() != null){
          s.getTarget().setStatus(TaxonomicStatus.ACCEPTED);
          NameUsageBase nu = new Taxon(s.getTarget());
          var m = matcher.match(baseReleaseKey, nu, (Classification)null);
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
    matchBaseReleaseIfNeeded();

    mergeSectors();

    updateState(ImportState.PROCESSING);
    // detect and group basionyms
    if (xCfg.groupBasionyms) {
      final var prios = new SectorPriority(getDatasetKey(), factory);
      var hc = HomotypicConsolidator.entireDataset(factory, newDatasetKey, prios::priority);
      hc.setBasionymExclusions(xCfg.basionymExclusions);
      hc.consolidate();

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
    // update sector metrics. The entire releases metrics are done later by the superclass
    buildSectorMetrics();
    // finally also call the shared part
    super.finalWork();
  }

  private void matchBaseReleaseIfNeeded() throws InterruptedException {
    updateState(ImportState.PROCESSING);
    boolean matched = false;
    final int testSize = 10000;
    try (SqlSession session = factory.openSession(false)) {
      var nmm = session.getMapper(NameMatchMapper.class);
      var nm = session.getMapper(NameMapper.class);
      var unmatched = nm.listUnmatch(newDatasetKey, NameIndexImpl.INDEX_NAME_TYPES, testSize);
      if (unmatched == null || unmatched.size() < testSize) {
        // rematch individually
        if (unmatched != null) {
          LOG.warn("Match {} usages from the base release without matching", unmatched.size());
          for (var n : unmatched) {
            NameMatch m = ni.match(n, true, false);
            if (m.hasMatch()) {
              nmm.create(n, n.getSectorKey(), m.getName().getKey(), m.getType());
            } else {
              LOG.info("No match for {}: {}", n.getKey(), n.getLabel());
            }
          }
        }
        session.commit();
        matched = true;
      }
    }
    // match outside the session if needed
    if (!matched) {
      LOG.warn("Rematch entire base release lacking > {} matches", testSize);
      DatasetMatcher dm = new DatasetMatcher(factory, ni);
      dm.match(newDatasetKey, true);
    }
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

  /**
   * We do all extended work here, e.g. sector merging
   */
  private void mergeSectors() throws Exception {
    // prepare merge handler config instance
    mergeCfg = new TreeMergeHandlerConfig(factory, xCfg, newDatasetKey, user);
    updateState(ImportState.INSERTING);
    final int size = sectors.size();
    int counter = 0;
    int failedSyncs = 0;
    for (Sector s : sectors) {
      LOG.info("Merge {}. #{} out of {}", s, counter++, size);
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
      checkIfCancelled();
      SectorSync ss;
      try {
        ss = syncFactory.release(s, newDatasetKey, mergeCfg, fullUser);
        ss.run();
        if (ss.getState().getState() != ImportState.FINISHED){
          failedSyncs++;
          LOG.error("Failed to sync {} with error: {}", s, ss.getState().getError());
        } else {
          // copy sync attempt to local instances as it finished successfully
          s.setSyncAttempt(ss.getState().getAttempt());
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
    LOG.error("All {} sectors merged, {} failed", counter, failedSyncs);
  }

  private void cleanImplicitTaxa() {
    LOG.warn("Clean implicit taxa - not implemented");
  }

  /**
   * Iterates over the entire tree of accepted names, validates taxa and resolves data. In particular this is:
   *
   *  1) flag parent name mismatches
   * Goes through all accepted species and infraspecies and makes sure the name matches the genus, species classification.
   * For example an accepted species Picea alba with a parent genus of Abies is imperfect, but as a result of homotypic grouping
   * and unresolved taxonomic word in sources a reality.
   *
   * Badly classified names are assigned the doubtful status and an NameUsageIssue.NAME_PARENT_MISMATCH is flagged
   *
   *  2) add missing autonyms if needed
   *
   *  3) remove empty genera generated in the xrelease (can be configured to be skipped)
   *
   */
  private void validateAndCleanTree() {
    LOG.info("Clean and validate entire xrelease {}", newDatasetKey);
    try (SqlSession session = factory.openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      TreeTraversalParameter params = new TreeTraversalParameter();
      params.setDatasetKey(newDatasetKey);
      params.setSynonyms(false);

      final var consumer = new TreeCleanerAndValidator(factory, newDatasetKey, xCfg.removeEmptyGenera);
      PgUtils.consume(() -> num.processTreeSimple(params), consumer);
    }
  }

  private void resolveDuplicateAcceptedNames() {
    LOG.info("Resolve duplicate accepted names");
  }

}
