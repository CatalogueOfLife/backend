package life.catalogue.release;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSync;
import life.catalogue.assembly.SycnException;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.assembly.TreeMergeHandlerConfig;
import life.catalogue.basgroup.HomotypicConsolidator;
import life.catalogue.basgroup.SectorPriority;
import life.catalogue.common.date.DateUtils;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.db.CopyDataset;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.RematchMissing;
import life.catalogue.matching.UsageMatcherGlobal;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import jakarta.validation.Validator;

import static life.catalogue.api.util.ObjectUtils.coalesce;

public class XRelease extends ProjectRelease {
  private static final Logger LOG = LoggerFactory.getLogger(XRelease.class);
  private final int baseReleaseKey;
  private final SectorImportDao siDao;
  // sectors as in the release with target pointing to the release usage identifiers
  private List<Sector> sectors;
  private DSID<Integer> sectorProjectKey;
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
           int releaseKey, int userKey, ReleaseConfig cfg, DoiConfig doiCfg, URI apiURI, URI clbURI, CloseableHttpClient client, ExportManager exportManager,
           DoiService doiService, DoiUpdater doiUpdater, Validator validator) {
    super("releasing extended", factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, releaseKey, userKey, cfg, doiCfg, apiURI, clbURI, client, exportManager, doiService, doiUpdater, validator);
    this.siDao = siDao;
    this.syncFactory = syncFactory;
    this.matcher = matcher;
    this.ni = matcher.getNameIndex();
    baseReleaseKey = releaseKey;
    fullUser.setKey(userKey);
    sectorProjectKey = DSID.root(projectKey);
    LOG.info("Build extended release for project {} from public release {}", projectKey, baseReleaseKey);
  }

  @VisibleForTesting
  void setCfg(XReleaseConfig xCfg) {
    this.xCfg = xCfg;
  }

  @Override
  protected void loadConfigs() {
    prCfg = loadConfig(XReleaseConfig.class, settings.getURI(Setting.XRELEASE_CONFIG), true);
    verifyConfigTemplates();
    xCfg = (XReleaseConfig) prCfg;
  }

  @Override
  protected void modifyDataset(Dataset d) {
    super.modifyDataset(d);
    d.setOrigin(DatasetOrigin.XRELEASE);
  }

  public static class XReleaseWrapper extends CitationUtils.ReleaseWrapper {
    final int baseSources;
    final int mergeSources;

    public XReleaseWrapper(CitationUtils.ReleaseWrapper data, int baseSources, int mergeSources) {
      super(data);
      this.baseSources = baseSources;
      this.mergeSources = mergeSources;
    }

    public int getBaseSources() {
      return baseSources;
    }

    public int getMergeSources() {
      return mergeSources;
    }

    public int getAllSources() {
      return baseSources + mergeSources;
    }
  }

  @Override
  protected CitationUtils.ReleaseWrapper metadataTemplateData(CitationUtils.ReleaseWrapper data) {
    // we can only calculate the real numbers later, when we know about all sectors
    // we will update the description at that point - the rest of the metadata should not use the source number variables !!!
    return new XReleaseWrapper(data, 1, 1);
  }

  @Override
  void prepWork() throws Exception {
    // fail early if components are not ready
    syncFactory.assertComponentsOnline();
    // ... or licenses of existing sectors are not compatible
    final License projectLicense = dataset.getLicense();
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var sm = session.getMapper(SectorMapper.class);
      Set<Integer> sourceKeys = new HashSet<>();
      for (var s : sm.listByPriority(projectKey, Sector.Mode.MERGE)) {
        if (!sourceKeys.contains(s.getSubjectDatasetKey())) {
          Dataset src = dm.get(s.getSubjectDatasetKey());
          if (!License.isCompatible(src.getLicense(), projectLicense)) {
            LOG.warn("License {} of project {} is not compatible with license {} of source {}: {}", projectLicense, projectKey, src.getLicense(), src.getKey(), src.getTitle());
            throw new IllegalArgumentException("Source license " +src.getLicense()+ " of " + s + " is not compatible with license " +projectLicense+ " of project " + projectKey);
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
      var publisher = pm.listAll(projectKey);
      // create missing sectors in project from publishers for compatible licenses only
      for (var p : publisher) {
        int newSectors = sDao.createMissingMergeSectorsFromPublisher(projectKey, fullUser.getKey(), p.getId(), xCfg.sourceDatasetExclusion);
        LOG.info("Created {} newly published merge sectors in project {} from publisher {} {}", newSectors, projectKey, p.getAlias(), p.getId());
      }
    }

    // load all merge sectors from project as they not exist in the base release
    // note that target taxa still refer to temp identifiers used in the project, not the stable ids from the base release
    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sectors = sm.listByPriority(projectKey, Sector.Mode.MERGE);
      // make sure dataset still exists for MERGE sectors
      var iter = sectors.iterator();
      while(iter.hasNext()){
        var s = iter.next();
        var src = DatasetInfoCache.CACHE.info(s.getSubjectDatasetKey(), true);
        if (src.deleted) {
          // remove sector
          LOG.warn("Removed merge sector {} as underlying dataset {} was deleted", s, src.key);
          sm.delete(s);
          iter.remove();
        }
        // move sector to release and rematch targets to base release
        rematchTarget(s, baseReleaseKey, matcher);
        s.setDatasetKey(newDatasetKey);
      }
    }

    // update description
    if (prCfg.metadata.description != null) {
      final Set<Integer> baseSources = new HashSet<>();
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        SectorSearchRequest req = new SectorSearchRequest();
        req.setMode(Set.of(Sector.Mode.ATTACH, Sector.Mode.UNION));
        req.setDatasetKey(baseReleaseKey);
        PgUtils.consume(()->sm.processSearch(req), s -> {
          baseSources.add(s.getSubjectDatasetKey());
        });
      }
      int numMerge = (int) sectors.stream().map(Sector::getSubjectDatasetKey).distinct().count();
      var data = new XReleaseWrapper(new CitationUtils.ReleaseWrapper(newDataset, base, dataset), baseSources.size(), numMerge);
      newDataset.setDescription( CitationUtils.fromTemplate(data, prCfg.metadata.description) );
    }

    // DOI
    createReleaseDOI();

    // setup id generator
    usageIdGen = new XIdProvider(projectKey, attempt, newDatasetKey, cfg, ni, factory);

    // make sure the missing matching is completed before we deal with the real data
    thread.join();
  }

  public static void rematchTarget(Sector s, int targetDatasetKey, UsageMatcherGlobal matcher) {
    if (s.getTarget() != null && targetDatasetKey != s.getDatasetKey()) {
      LOG.info("Rematch sector target {} to dataset {}", s.getTarget(), targetDatasetKey);
      s.getTarget().setStatus(TaxonomicStatus.ACCEPTED);
      NameUsageBase nu = new Taxon(s.getTarget());
      var m = matcher.match(targetDatasetKey, nu, null, true, false);
      if (m.isMatch()) {
        s.getTarget().setBroken(false);
        s.getTarget().setId(m.getId());
      } else {
        LOG.warn("Failed to match target {} of sector {}[{}] to dataset {}!", s.getTarget(), s.getId(), s.getSubjectDatasetKey(), targetDatasetKey);
        s.setTarget(null);
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
    updateTmpIDs();
    processWithPrio();

    // flagging of suspicious usages
    validateAndCleanTree();
    cleanImplicitTaxa();

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

  private void processWithPrio() {
    final var prios = new SectorPriority(getDatasetKey(), factory);
    // detect and group basionyms
    if (xCfg.homotypicConsolidation) {
      final LocalDateTime start = LocalDateTime.now();
      var hc = HomotypicConsolidator.entireDataset(factory, newDatasetKey, prios::priority);
      if (xCfg.basionymExclusions != null) {
        hc.setBasionymExclusions(xCfg.basionymExclusions);
      }
      hc.consolidate(xCfg.homotypicConsolidationThreads);
      DateUtils.logDuration(LOG, hc.getClass(), start);

    } else {
      LOG.warn("Homotypic grouping disabled in xrelease configs");
    }

    flagDuplicatesAsProvisional(prios);
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
      //TODO: rewrite to test all in memory, using the int values of the stable ids or create negative ones for non stable ids and store a mapping on disk mapdb
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
    // we copy publisher entities from the project, all the rest from the base release with the new ids already
    if (entity.equals(Publisher.class)) {
      super.copyTable(entity, mapperClass, session);

    } else {
      // copy all data from the base release
      int count = session.getMapper(mapperClass).copyDataset(baseReleaseKey, newDatasetKey, false);
      LOG.info("Copied {} {}s from {} to {}", count, entity.getSimpleName(), baseReleaseKey, newDatasetKey);
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
        var sim = siDao.getAttempt(sectorProjectKey.id(s.getId()), s.getSyncAttempt());
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
      if (usageIdGen != null) {
        usageIdGen.close();
      }
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
        if (!sm.exists(s)) {
          sm.createWithID(s);
        }
      }
      checkIfCancelled();
      SectorSync ss;
      try {
        // this loads decisions from the main project, even though the sector dataset key is the xrelease
        ss = syncFactory.release(s, newDatasetKey, mergeCfg, nameIdGen, typeMaterialIdGen, usageIdGen, fullUser.getKey());
        ss.run();
        if (ss.getState().getState() != ImportState.FINISHED){
          failedSyncs++;
          if (mergeCfg.xCfg.failOnSyncErrors) {
            throw new SycnException(ss.lastException());
          }
          LOG.error("Failed to sync {} with error: {}", s, ss.getState().getError(), ss.lastException());
        } else {
          // copy remaining merge decisions
          copyMergeDecisions(ss.getDecisions().values());
          // copy attempts to local instances as it finished successfully
          s.setSyncAttempt(ss.getState().getAttempt());
          // and also update our release copy!
          try (SqlSession session = factory.openSession(true)) {
            SectorMapper sm = session.getMapper(SectorMapper.class);
            sm.updateReleaseAttempts(DSID.of(projectKey, s.getId()), newDatasetKey);
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
    DateUtils.logDuration(LOG, "Merging sectors", start);
  }

  /**
   * We use tmp uuids for names initially created without authorship, see https://github.com/CatalogueOfLife/backend/issues/1407
   * Assign final, stable ids to those.
   * @throws Exception
   */
  private void updateTmpIDs() throws Exception {
    // load them into memory so we can modify them later without breaking the cursor
    List<String> tmpIDs = new ArrayList<>();
    try (SqlSession session = factory.openSession(false)) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processIds(newDatasetKey, true, 16), tmpIDs::add);
    }
    LOG.info("Found {} temporary IDs to be converted into stable IDs in release {}", tmpIDs.size(), newDatasetKey);

    int counter = 0;
    try (SqlSession session = factory.openSession(false)) {
      var num = session.getMapper(NameUsageMapper.class);
      for (var id : tmpIDs) {
        var key = DSID.of(newDatasetKey, id);
        var sn = num.getSimpleCached(key);
        String stableID = usageIdGen.issue(sn);
        TaxonDao.changeUsageID(key, stableID, sn.isSynonym(), user, session);
        matcher.getUsageCache().invalidateAll();
        counter++;
        if (counter % 100 == 0) {
          session.commit();
        }
      }
      session.commit();
    }
    LOG.info("Issued stable IDs for {} temporary canonical name usages in release {}", counter, newDatasetKey);
  }

  private void copyMergeDecisions(Collection<EditorialDecision> decisions) {
    int counter = 0;
    int existed = 0;
    try (SqlSession session = factory.openSession(false)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      for (var d : decisions) {
        // we create decisions on the fly to auto block - ignore those
        if (d.getId() != null) {
          d.setDatasetKey(newDatasetKey);
          if (!dm.existsWithKeyOrSubject(d)) {
            dm.createWithID(d);
            counter++;
          } else {
            existed++;
          }
        }
      }
      session.commit();
      LOG.info("Copied {} new merge decisions to {}. {} already existed", counter, newDatasetKey, existed);
    }
  }

  public int getFailedSyncs() {
    return failedSyncs;
  }

  /**
   * Goes through all accepted infraspecies and checks if a matching autonym exists,
   * creating missing autonyms where needed.
   * An autonym is an infraspecific taxon that has the same species and infraspecific epithet.
   * We do this last to not persistent autonyms that we dont need after basionyms are grouped or status has changed for some other reason.
   *
   * Updates implicit names to be accepted (not doubtful) and removes implicit taxa with no children if configured to do so.
   */
  private void cleanImplicitTaxa() {
    LOG.warn("Clean implicit taxa - not implemented");
  }

  /**
   * Iterates over the entire tree of accepted names, validates taxa and resolves data.
   */
  private void validateAndCleanTree() {
    LOG.info("Clean, validate & produce taxon metrics for entire xrelease {}", newDatasetKey);
    final AtomicInteger counter = new AtomicInteger();
    final LocalDateTime start = LocalDateTime.now();
    try (SqlSession sessionRO = factory.openSession(true);
         SqlSession session = factory.openSession(false);
         var consumer = new TreeCleanerAndValidator(factory, newDatasetKey, xCfg.removeEmptyGenera)
    ) {
      // add metrics generator to tree traversal
      var stack = consumer.stack();
      MetricsBuilder mb = new MetricsBuilder(MetricsBuilder.tracker(stack), newDatasetKey, session);
      stack.addHandler(new ParentStack.StackHandler<>() {
        @Override
        public void start(TreeCleanerAndValidator.XLinneanNameUsage n) {
          mb.start(n.getId());
        }

        @Override
        public void end(ParentStack.SNC<TreeCleanerAndValidator.XLinneanNameUsage> n) {
          mb.end(n.usage, n.usage.getSectorKey(), n.usage.getExtinct());
          if (counter.incrementAndGet() % 5000 == 0) {
            session.commit();
          }
        }
      });
      // traverse accepted tree
      var num = sessionRO.getMapper(NameUsageMapper.class);
      TreeTraversalParameter params = new TreeTraversalParameter();
      params.setDatasetKey(newDatasetKey);
      params.setSynonyms(false);

      PgUtils.consume(() -> num.processTreeLinneanUsage(params, true, false), consumer);
      stack.flush();
      session.commit();
      metrics.setMaxClassificationDepth(consumer.getMaxDepth());
      LOG.info("{} usages out of {} flagged with issues during validation", consumer.getFlagged(), consumer.getCounter());

    } catch (Exception e) {
      LOG.error("Name validation, cleaning & metrics failed", e);
    }
    DateUtils.logDuration(LOG, TreeCleanerAndValidator.class, start);
  }


  /**
   * Assigns a doubtful status to accepted names that only differ in authorship
   */
  private void flagDuplicatesAsProvisional(SectorPriority prios) {
    LOG.info("Find homonyms and mark as provisional");
    final LocalDateTime start = LocalDateTime.now();
    try (SqlSession session = factory.openSession(false)) {
      var num = session.getMapper(NameUsageMapper.class);
      var dum = session.getMapper(DuplicateMapper.class);
      var vsm = session.getMapper(VerbatimSourceMapper.class);
      // same names with the same rank and code
      var dupes = dum.homonyms(newDatasetKey, Set.of(TaxonomicStatus.ACCEPTED));
      LOG.info("Marking {} homonyms as provisional", dupes.size());

      int counter = 0;
      final DSID<String> key = DSID.root(newDatasetKey);
      for (var d : dupes) {
        int min = Integer.MAX_VALUE;
        for (var u : d.getUsages()) {
          min = Math.min(min, prios.priority(u.getSectorKey()));
        }
        for (var u : d.getUsages()) {
          if (prios.priority(u.getSectorKey()) > min) {
            num.updateStatus(key.id(u.getId()), TaxonomicStatus.PROVISIONALLY_ACCEPTED, user);
            vsm.addIssue(key, Issue.DUPLICATE_NAME);
            if (counter++ % 1000 == 0) {
              session.commit();
            }
          }
        }
      }
      session.commit();
      LOG.info("Changed {} homonyms to provisional status", counter);

    } catch (Exception e) {
      LOG.error("Homonym flagging failed", e);
    }
    DateUtils.logDuration(LOG, "Homonym flagging", start);
  }

}
