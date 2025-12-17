package life.catalogue.release;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSync;
import life.catalogue.assembly.SyncException;
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
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.*;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import jakarta.validation.Validator;

public class XRelease extends ProjectRelease {
  private static final Logger LOG = LoggerFactory.getLogger(XRelease.class);
  private final int baseReleaseKey;
  private final SectorImportDao siDao;
  // sectors as in the release with target pointing to the release usage identifiers
  private List<Sector> sectors;
  private DSID<Integer> sectorProjectKey;
  private final User fullUser = new User();
  private final SyncFactory syncFactory;
  private final UsageMatcherFactory matcherFactory;
  private final NameIndex ni;
  private UsageMatcher matcher;
  private XReleaseConfig xCfg;
  private TreeMergeHandlerConfig mergeCfg;
  private XIdProvider usageIdGen;
  private int failedSyncs;
  private int tmpProjectKey;

  XRelease(SqlSessionFactory factory, SyncFactory syncFactory, UsageMatcherFactory matcherFactory, NameIndex nidx, NameUsageIndexService indexService, ImageService imageService,
           DatasetDao dDao, DatasetImportDao diDao, SectorImportDao siDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
           int releaseKey, int userKey, ReleaseConfig cfg, URI apiURI, URI clbURI, CloseableHttpClient client, ExportManager exportManager, Validator validator
  ) {
    super("releasing extended", factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, releaseKey, userKey, cfg, apiURI, clbURI, client, exportManager, validator);
    this.siDao = siDao;
    this.syncFactory = syncFactory;
    this.matcherFactory = matcherFactory;
    this.ni = nidx;
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
  void initJob() throws Exception {
    // this creates the newDatasetKey - our final destination
    super.initJob();
    // we do copy the base release into a new tmp dataset that we merge into
    // and finally copy all data over to map the stable ids - just like we do with regular releases
    Dataset d = new Dataset();
    d.setTitle("Merge Project " + newDatasetKey);
    d.setOrigin(DatasetOrigin.XRELEASE);
    d.setSourceKey(projectKey);
    d.setPrivat(true);
    d.setType(newDataset.getType());
    d.setLicense(newDataset.getLicense());
    tmpProjectKey = dDao.createTemp(d, user);
    idMapDatasetKey = tmpProjectKey;

    // create sequences
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetPartitionMapper.class).createSequences(tmpProjectKey);;
    }
    createIdMapTables();

    // create matcher against temp - this has no data yet, so we need to load the matcher once more after copying the base!
    this.matcher = matcherFactory.memory(tmpProjectKey);
    LOG.info("Created temporary project {} which will cleanup by itself in a few days", tmpProjectKey);
  }

  @Override
  void prepWork() throws Exception {
    // fail early if components are not ready
    syncFactory.assertComponentsOnline();
    // ... or licenses of existing sectors are not compatible
    licenseCheck();

    // make sure the base release is fully matched
    // runs in parallel to the rest of the prep phase below
    Runnable matchMissingTask = new RematchMissing(factory, ni, null, baseReleaseKey);
    final var thread = ExecutorUtils.runInNewThread(matchMissingTask);

    // add new publisher sectors
    updatePublisherSectors();

    // load all merge sectors from project as they do not exist in the base release
    loadMergeSectors();

    // make sure the missing matching is completed before we deal with the real data
    thread.join();

    // copy base release to tmp project - we keep all identifiers
    final int xreleaseDatasetKey = newDatasetKey;
    newDatasetKey = tmpProjectKey;
    copyData();

    // prepare configs, create incertae sedis
    mergeCfg = new TreeMergeHandlerConfig(factory, xCfg, newDatasetKey, user);

    // load matcher
    this.matcher.store().load(factory);

    // setup id generator
    usageIdGen = new XIdProvider(projectKey, tmpProjectKey, attempt, xreleaseDatasetKey, cfg, prCfg, ni, factory);
    usageIdGen.removeIdsFromDataset(tmpProjectKey);

    assertNoSynonymParents();
    mergeSectors();
    assertNoSynonymParents();

    // sanitize merges
    homotypicGrouping();
    assertNoSynonymParents();

    // flagging
    validateAndCleanTree();
    assertNoSynonymParents();
    cleanImplicitTaxa();
    assertNoSynonymParents();
    flagLoops();
    assertNoSynonymParents();

    // remove orphan names and references
    removeOrphans(tmpProjectKey);
    assertNoSynonymParents();

    // stable ids
    mapTmpIDs();

    // switch back to final release for the main copy phase
    newDatasetKey = xreleaseDatasetKey;

    // update metadata
    updateMetadata();

    // prev release
    try (SqlSession session = factory.openSession(true)) {
      prevReleaseKey = session.getMapper(DatasetMapper.class).previousRelease(newDatasetKey);
    }
  }

  @Override
  protected void metrics() throws InterruptedException {
    // create sector metrics
    buildSectorMetrics();
    // main metrics
    super.metrics();
  }

  @Override
  void finalWork() throws Exception {
    super.finalWork();
    // finally report about IDs which access names from the final release dataset
    checkIfCancelled();
    var start = LocalDateTime.now();
    usageIdGen.report();
    DateUtils.logDuration(LOG, "ID reporting", start);
    // drop tmp project sequences
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetPartitionMapper.class).deleteSequences(tmpProjectKey);;
    }
  }

  private void licenseCheck() {
    final License projectLicense = dataset.getLicense();
    LOG.info("Checking source licenses against project license {} ...", projectLicense);
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
  }

  private void updatePublisherSectors() {
    LOG.info("Updating publisher sectors");
    try (SqlSession session = factory.openSession(true)) {
      var pm = session.getMapper(SectorPublisherMapper.class);
      var publisher = pm.listAll(projectKey);
      // create missing sectors in project from publishers for compatible licenses only
      for (var p : publisher) {
        int newSectors = sDao.createMissingMergeSectorsFromPublisher(projectKey, fullUser.getKey(), p.getId(), xCfg.sourceDatasetExclusion);
        LOG.info("Created {} newly published merge sectors in project {} from publisher {} {}", newSectors, projectKey, p.getAlias(), p.getId());
      }
    }
  }

  private void loadMergeSectors() {
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
        // move sector to release
        // we don't persist the sectors yet - this happens when we sync them in mergeSectors()
        s.setDatasetKey(tmpProjectKey);
      }
    }
  }

  private void assertNoSynonymParents() {
    try (SqlSession session = factory.openSession(false)) {
      if (session.getMapper(NameUsageMapper.class).hasParentSynoynms(newDatasetKey)) {
        throw new IllegalStateException("XRelease introduced parent synonyms");
      }
    }
  }

  protected void homotypicGrouping() throws InterruptedException {
    checkIfCancelled();
    // detect and group basionyms
    final var prios = new SectorPriority(getDatasetKey(), factory);
    if (xCfg.homotypicConsolidation) {
      final LocalDateTime start = LocalDateTime.now();
      var hc = HomotypicConsolidator.entireDataset(factory, newDatasetKey, prios::priority);
      if (xCfg.basionymExclusions != null) {
        hc.setBasionymExclusions(xCfg.basionymExclusions);
      }
      if (xCfg.misspellingConsolidation) {
        hc.consolidateMisspellings();
      }
      hc.consolidate(xCfg.homotypicConsolidationThreads);
      DateUtils.logDuration(LOG, hc.getClass(), start);

    } else {
      LOG.warn("Homotypic grouping disabled in xrelease configs");
    }

    // finally flag duplicates with different authors as provisional
    if (xCfg.flagDuplicatesAsProvisional) {
      flagDuplicatesAsProvisional(prios);
    }
  }

  private void updateMetadata() throws InterruptedException {
    checkIfCancelled();
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

    newDataset.appendNotes(String.format("Base release %s.", baseReleaseKey));
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetMapper.class).update(newDataset);
    }
  }

  /**
   * flag loops, synonyms pointing to synonyms and nonexisting parents
   */
  protected void flagLoops() throws InterruptedException {
    checkIfCancelled();
    // any chained synonyms?
    try (SqlSession session = factory.openSession(false)) {
      var adder = new IssueAdder(tmpProjectKey, session);
      var chains = session.getMapper(NameUsageMapper.class).detectChainedSynonyms(newDatasetKey);
      if (chains != null && !chains.isEmpty()) {
        LOG.error("{} chained synonyms found in XRelease {}", chains.size(), newDatasetKey);

        var num = session.getMapper(NameUsageMapper.class);
        var key = DSID.<String>root(tmpProjectKey);

        for (var id : chains) {
          key.id(id);
          var syn = num.getSimpleVerbatim(key);
          num.updateParentId(key, syn.getParentId(), user);
          adder.addIssue(syn.getVerbatimSourceKey(), id, Issue.CHAINED_SYNONYM);
          session.commit();
        }
      }
      // any accepted names below synonyms? Move to accepted
      var synParents = session.getMapper(NameUsageMapper.class).detectParentSynoynms(newDatasetKey);
      if (synParents != null && !synParents.isEmpty()) {
        LOG.error("{} taxa found in XRelease {} with synonyms as their parent", synParents.size(),newDatasetKey);
        var num = session.getMapper(NameUsageMapper.class);
        var key = DSID.<String>root(tmpProjectKey);
        for (var id : synParents) {
          key.id(id);
          var syn = num.getSimpleParent(key);
          num.updateParentId(key, syn.getParentId(), user);
          adder.addIssue(syn.getVerbatimSourceKey(), id, Issue.SYNONYM_PARENT);
          session.commit();
        }
      }

      // cut potential cycles in the tree?
      var cycles = session.getMapper(NameUsageMapper.class).detectLoop(newDatasetKey);
      if (cycles != null && !cycles.isEmpty()) {
        LOG.error("{} cycles found in the parent-child classification of dataset {}", cycles.size(), newDatasetKey);
        var tm = session.getMapper(TaxonMapper.class);
        var num = session.getMapper(NameUsageMapper.class);

        Name n = Name.newBuilder()
          .id("cycleParentPlaceholder")
          .datasetKey(tmpProjectKey)
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

        session.commit();
        final DSID<String> key = DSID.root(tmpProjectKey);
        for (String id : cycles) {
          num.updateParentId(key, cycleParent.getId(), user);
          adder.addIssue(id, Issue.PARENT_CYCLE);
          session.commit();
        }
        LOG.warn("Resolved {} cycles found in the parent-child classification of dataset {}", cycles.size(), newDatasetKey);
      }

      // look for non existing parents
      var num = session.getMapper(NameUsageMapper.class);
      var missing = num.listMissingParentIds(tmpProjectKey);
      if (missing != null && !missing.isEmpty()) {
        LOG.error("{} usages found with a non existing parentID", missing.size());
        final String parent;
        if (mergeCfg.hasIncertae()) {
          parent = mergeCfg.incertae.getId();
        } else {
          parent = null;
        }
        final DSID<String> key = DSID.root(tmpProjectKey);
        for (String id : missing) {
          num.updateParentId(key, parent, user);
          adder.addIssue(id, Issue.PARENT_ID_INVALID);
          session.commit();
        }
        LOG.warn("Resolved {} usages with a non existing parent in dataset {}", missing.size(),newDatasetKey);
      }
      session.commit();
    }
  }

  /**
   * We copy the tables of the base release here, not the project
   */
  @Override
  <M extends CopyDataset> void copyTable(Class entity, Class<M> mapperClass, SqlSession session) throws InterruptedException {
    int from;
    boolean map;
    checkIfCancelled();
    if (newDatasetKey != tmpProjectKey) {
      // this is the second copy step from the tmpProject to the actual release using the mapped IDs!
      from = tmpProjectKey;
      map = true;
    } else {
      map = false;
      if (entity.equals(SectorPublisher.class)) {
        // we copy publisher entities from the project and all the rest from the base release with the new ids already
        from = projectKey;
      } else {
        // copy all data from the base release
        from = baseReleaseKey;
      }
    }

    int count = session.getMapper(mapperClass).copyDataset(from, newDatasetKey, map);
    session.commit();
    LOG.info("Copied {} {}s from {} to {}", count, entity.getSimpleName(), from, newDatasetKey);
  }

  /**
   * This updates the merge sector metrics with the final counts.
   * We do this at the very end as homotypic grouping and other final changes have impact on the sectors.
   */
  private void buildSectorMetrics() throws InterruptedException {
    checkIfCancelled();
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

  protected void mergeSectors() throws Exception {
    mergeSectors(Integer.MAX_VALUE);
  }

  /**
   * We do all extended work here, e.g. sector merging
   */
  protected void mergeSectors(int maxSectors) throws Exception {
    checkIfCancelled();
    // prepare merge handler config instance
    LOG.info("Start merging {} sectors", sectors.size());
    final LocalDateTime start = LocalDateTime.now();
    final int size = sectors.size();
    int counter = 0;
    failedSyncs = 0;
    // sequential id generators for extended records
    final Supplier<String> nameIdGen = new XIdGen();
    final Supplier<String> typeMaterialIdGen = new XIdGen();
    updateState(ImportState.INSERTING);
    for (Sector s : sectors) {
      if (counter >= maxSectors) {
        LOG.warn("Stop merging as we reached the debug limit of {} sectors", maxSectors);
        break;
      }

      LOG.info("Merge {}. #{} out of {}", s, counter++, size);
      // the sector might not have been copied to the xrelease yet - we only copied all sectors from the base release, not the project.
      // create only if missing
      try (SqlSession session = factory.openSession(true)) {
        SectorMapper sm = session.getMapper(SectorMapper.class);
        if (!sm.exists(s)) {
          // the sector belongs to the tmp project,
          // but the targetID points to the project ids, not the tmp dataset ids which use the stable base release identifiers
          SectorSync.rematchSectorTarget(s, s.getDatasetKey(), session);
          sm.createWithID(s);
        }
      }
      checkIfCancelled();
      SectorSync ss;
      try {
        // sector syncs require the project key where we store all sync attempts
        var skey = DSID.of(projectKey, s.getId());
        ss = syncFactory.release(skey, tmpProjectKey, mergeCfg, matcher, nameIdGen, typeMaterialIdGen, usageIdGen, fullUser.getKey());
        ss.run();
        if (ss.getState().getState() != ImportState.FINISHED){
          failedSyncs++;
          if (mergeCfg.xCfg.failOnSyncErrors) {
            throw new SyncException(ss.lastException());
          }
          LOG.error("Failed to sync {} with state={}, error={}", s, ss.getState().getState(), ss.getState().getError(), ss.lastException());
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
    matcher=null; // release matcher memory
  }

  /**
   * We use tmp uuids for names initially created without authorship, see https://github.com/CatalogueOfLife/backend/issues/1407
   * Assign final, stable ids to those.
   * @throws Exception
   */
  private void mapTmpIDs() throws InterruptedException {
    checkIfCancelled();
    var start = LocalDateTime.now();
    final int startKey = usageIdGen.peek();
    LOG.debug("Next key for stable IDs before mapping will be {}", startKey);
    usageIdGen.mapTempIds();
    DateUtils.logDuration(LOG, "ID provider", start);
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
  protected void cleanImplicitTaxa() throws InterruptedException {
    checkIfCancelled();
    LOG.warn("Clean implicit taxa - not implemented");
  }

  /**
   * Iterates over the entire tree of accepted names, validates taxa and resolves data.
   */
  protected void validateAndCleanTree() throws InterruptedException {
    checkIfCancelled();
    LOG.info("Clean, validate & produce taxon metrics for entire xrelease {}", newDatasetKey);
    final AtomicInteger counter = new AtomicInteger();
    final LocalDateTime start = LocalDateTime.now();
    try (SqlSession sessionRO = factory.openSession(true);
         SqlSession session = factory.openSession(false)
    ) {
      var consumer = new TreeCleanerAndValidator(session, newDatasetKey, xCfg.removeEmptyGenera);
      // add metrics generator to tree traversal
      var stack = consumer.stack();
      TaxonMetricsBuilder mb = new TaxonMetricsBuilder(TaxonMetricsBuilder.tracker(stack), newDatasetKey, session);
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
      params.setSynonyms(true);

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
   * Goes through all accepted bi/trinomen and looks for names that are identical by rank and authorship
   * and only differ by one letter in their scientific name.
   *
   * The highest priority name stays, the other becomes a synonym of it.
   */
  private void synonymizeMisspelledBinomials(SectorPriority prios) throws InterruptedException {
    checkIfCancelled();
    LOG.info("Find misspelled names and synonymize them");
    final LocalDateTime start = LocalDateTime.now();
    try (SqlSession session = factory.openSession(false)) {
      var num = session.getMapper(NameUsageMapper.class);
      var dum = session.getMapper(DuplicateMapper.class);
      var adder = new IssueAdder(newDatasetKey, session);
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
            adder.addIssue(u.getId(), Issue.DUPLICATE_NAME);
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

  /**
   * Assigns a doubtful status to accepted names that only differ in authorship
   */
  private void flagDuplicatesAsProvisional(SectorPriority prios) throws InterruptedException {
    checkIfCancelled();
    LOG.info("Find homonyms and mark as provisional");
    final LocalDateTime start = LocalDateTime.now();
    try (SqlSession session = factory.openSession(false)) {
      var num = session.getMapper(NameUsageMapper.class);
      var dum = session.getMapper(DuplicateMapper.class);
      var adder = new IssueAdder(newDatasetKey, session);
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
            adder.addIssue(u.getId(), Issue.DUPLICATE_NAME);
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
