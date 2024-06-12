package life.catalogue.dao;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DoiChange;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;

import static life.catalogue.metadata.MetadataFactory.stripHtml;

/**
 * A DAO for datasets that orchestrates the needs for partitioning tables and removing dataset remains
 * in other areas, e.g. exports, import metrics, images.
 *
 * The Partitioning of CLB is dataset key based and differs between EXTERNAL datasets that live on the default partition
 * and PROJECT, RELEASE & X_RELEASE datasets that live on their own, dedicated partition.
 * When a new non external dataset is created, new tables need to be created and finally attached to the main tables.
 * Without a pre-existing constraint on the default partition to not include these dataset keys, postgres has to scan all default partiotions
 * which takes several minutes with a fully indexed checklistbank.
 *
 * To avoid this we generate dataset keys in the DAO with a predefined ratio keyProjectMod of datasets living on the dedicated vs default partitions.
 * On startup we load the current max keys from the database.
 */
public class DatasetDao extends DataEntityDao<Integer, Dataset, DatasetMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDao.class);
  private static final int TEMP_KEY_START = 100_000_000;
  private static final int TEMP_EXPIRY_DAYS = 7;
  private final NormalizerConfig nCfg;
  private final ReleaseConfig rCfg;
  private final ImporterConfig iCfg;
  private final DownloadUtil downloader;
  private final ImageService imgService;
  private final BiFunction<Integer, String, File> scratchFileFunc;
  private final DatasetImportDao diDao;
  private final DatasetExportDao exportDao;
  private final NameUsageIndexService indexService;
  private final EventBus bus;
  private final TempIdProvider tempIds;

  /**
   * @param scratchFileFunc function to generate a scrach dir for logo updates
   */
  public DatasetDao(SqlSessionFactory factory,
                    NormalizerConfig nCfg, ReleaseConfig rCfg, ImporterConfig iCfg,
                    DownloadUtil downloader,
                    ImageService imgService,
                    DatasetImportDao diDao,
                    DatasetExportDao exportDao,
                    NameUsageIndexService indexService,
                    BiFunction<Integer, String, File> scratchFileFunc,
                    EventBus bus,
                    Validator validator) {
    super(true, factory, Dataset.class, DatasetMapper.class, validator);
    this.nCfg = nCfg;
    this.rCfg = rCfg;
    this.iCfg = iCfg;
    if (iCfg.publisherAlias == null) iCfg.publisherAlias = new HashMap<>(); // avoids many null checks below
    this.downloader = downloader;
    this.imgService = imgService;
    this.scratchFileFunc = scratchFileFunc;
    this.diDao = diDao;
    this.exportDao = exportDao;
    this.indexService = indexService;
    this.bus = bus;
    this.tempIds = new TempIdProvider();
  }

  /**
   * For testing only!!!
   * THis is using mocks and misses real functionality, but simplifies the construction of the core dao.
   */
  @VisibleForTesting
  public DatasetDao(SqlSessionFactory factory, DownloadUtil downloader, DatasetImportDao diDao, Validator validator) {
    this(factory, new NormalizerConfig(), new ReleaseConfig(), new ImporterConfig(), downloader, ImageService.passThru(), diDao, null, NameUsageIndexService.passThru(), null, new EventBus(), validator);
  }

  public Dataset get(UUID gbifKey) {
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(mapperClass);
      return mapper.getByGBIF(gbifKey);
    }
  }

  public DatasetWithSettings getWithSettings(UUID gbifKey) {
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(mapperClass);
      return addSettings(mapper.getByGBIF(gbifKey), mapper);
    }
  }

  public DatasetWithSettings getWithSettings(Integer key) {
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(mapperClass);
      return addSettings(mapper.get(key), mapper);
    }
  }

  private DatasetWithSettings addSettings(Dataset d, DatasetMapper mapper) {
    DatasetWithSettings ds = null;
    if (d != null) {
      DatasetSettings s = mapper.getSettings(d.getKey());
      ds = new DatasetWithSettings(d, s);
    }
    return ds;
  }

  public Dataset patchMetadata(DatasetWithSettings ds, Dataset update) {
    return patchMetadata(ds, update, validator);
  }

  /**
   * Updates the given dataset ds with the provided metadata update,
   * retaining managed properties like keys and settings.
   * Mandatory properties like title and license are only changed if not null.
   * @param ds
   * @param update
   */
  public static Dataset patchMetadata(DatasetWithSettings ds, Dataset update, Validator validator) {
    final Dataset d = ds.getDataset();
    final boolean merge = ds.isEnabled(Setting.MERGE_METADATA);
    if (merge) {
      LOG.info("Merge dataset metadata {}: {}", d.getKey(), d.getTitle());
    }

    Set<String> nonNullProps = Set.of("title", "alias", "license");
    try {
      for (PropertyDescriptor prop : Dataset.PATCH_PROPS) {
        Object val = prop.getReadMethod().invoke(update);
        // for required property do not allow null
        if (val != null || (!merge && !nonNullProps.contains(prop.getName()))) {
          prop.getWriteMethod().invoke(d, val);
        }
      }
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    ObjectUtils.copyIfNotNull(update::getType, d::setType);
    // verify emails, orcids & rorid as they can break validation on insert
    d.processAllAgents(a -> a.validateAndNullify(validator));
    return d;
  }

  private void sanitize(Dataset d) {
    if (d != null) {
      if (d.getOrigin() == null) {
        throw new IllegalArgumentException("origin is required");
      }
      if (d.getType() == null) {
        d.setType(DatasetType.OTHER);
      }
      // remove null sources & agents
      if (d.getSource() != null && !d.getSource().isEmpty()) {
        try {
          d.getSource().removeIf(java.util.Objects::isNull);
        } catch (UnsupportedOperationException e) {
          // tests use immutable collections sometimes - ignore
        }
        // generate a citation ID if missing
        Set<String> ids = d.getSource().stream()
                           .map(Citation::getId)
                           .filter(java.util.Objects::nonNull)
                           .collect(Collectors.toSet());
        d.getSource().forEach(cite -> {
          if (cite.getId() == null) {
            String id = buildID(cite);
            String prefix = id;
            int x = 1;
            while (ids.contains(id)) {
              id = prefix + "-" + x++;
            }
            cite.setId(id);
          }
        });
      }
      if (d.getCreator() != null) {
        d.setCreator(removeNullOrEmpty(d.getCreator()));
      }
      if (d.getEditor() != null) {
        d.setEditor(removeNullOrEmpty(d.getEditor()));
      }
      if (d.getContributor() != null) {
        d.setContributor(removeNullOrEmpty(d.getContributor()));
      }
      if (d.getDescription() != null) {
        d.setDescription(stripHtml(d.getDescription()));
      }
      if (d.getTitle() != null) {
        d.setTitle(stripHtml(d.getTitle()));
      }
    }
  }

  /**
   * Returns a new (!) list with all nulls or empty agents removed.
   * We don't change the list in place to allow for immutable input
   */
  private static List<Agent> removeNullOrEmpty(List<Agent> c) {
    if (c == null) return null;
    return c.stream()
            .filter(a -> a != null && !a.isEmpty())
            .collect(Collectors.toList());
  }

  private static String buildID(Citation c) {
    StringBuilder sb = new StringBuilder();
    if (c.getAuthor() != null && !c.getAuthor().isEmpty()) {
      sb.append(c.getAuthor().get(0).getFamily());
    }
    if (c.getIssued() != null) {
      sb.append(c.getIssued().getYear());
    }
    return sb.toString();
  }
  public ResultPage<Dataset> list(Page page) {
    return super.list(DatasetMapper.class, page);
  }

  public DatasetSettings getSettings(int key) {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.getSettings(key);
    }
  }

  public void putSettings(int key, DatasetSettings settings, int userKey) {
    // verify templates
    verifySettings(settings);
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateSettings(key, settings, userKey);
      session.commit();
    }
  }

  /**
   * Verifies settings values, in particular the freemarker citation templates
   */
  static void verifySettings(DatasetSettings ds) throws IllegalArgumentException {
    Dataset d = new Dataset();
    d.setKey(1);
    d.setAlias("alias");
    d.setTitle("title");
    d.setOrigin(DatasetOrigin.PROJECT);
    d.setIssued(FuzzyDate.now());
    d.setLogo(URI.create("https://gbif.org"));
    d.setUrl(d.getLogo());
    d.setCreated(LocalDateTime.now());
    d.setModified(LocalDateTime.now());
    d.setImported(LocalDateTime.now());
    // try with all templates, throwing IAE if bad
    verifySetting(ds, Setting.RELEASE_ALIAS_TEMPLATE, d, null);
    verifySetting(ds, Setting.RELEASE_VERSION_TEMPLATE, d, null);
  }

  static void verifySetting(DatasetSettings ds, Setting setting, Dataset d, Dataset d2) throws IllegalArgumentException {
    if (ds.containsKey(setting)) {
      try {
        if (d2 == null) {
          CitationUtils.fromTemplate(d, ds.getString(setting));
        } else {
          CitationUtils.fromTemplate(d, d2, ds.getString(setting));
        }
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Bad template for " + setting + ": " + e.getMessage(), e);
      }
    }
  }

  public List<Integer> searchKeys(DatasetSearchRequest req) {
    try (SqlSession session = factory.openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.searchKeys(req, DatasetMapper.MAGIC_ADMIN_USER_KEY);
    }
  }

  public ResultPage<Dataset> search(@Nullable DatasetSearchRequest nullableRequest, @Nullable Integer userKey, @Nullable Page page) {
    page = page == null ? new Page() : page;
    final DatasetSearchRequest req = nullableRequest == null ? new DatasetSearchRequest() : nullableRequest;
    if (req.getSortBy() == null) {
      if (!StringUtils.isBlank(req.getQ())) {
        req.setSortBy(DatasetSearchRequest.SortBy.RELEVANCE);
      } else {
        req.setSortBy(DatasetSearchRequest.SortBy.KEY);
      }
    } else if (req.getSortBy() == DatasetSearchRequest.SortBy.RELEVANCE && StringUtils.isBlank(req.getQ())) {
      req.setQ(null);
      req.setSortBy(DatasetSearchRequest.SortBy.KEY);
    }
    
    try (SqlSession session = factory.openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      List<Dataset> result = dm.search(req, userKey, page);
      return new ResultPage<>(page, result, () -> dm.count(req, userKey));
    }
  }

  public List<Duplicate.IntKeys> listDuplicates(int minCount, @Nullable UUID publisherKey) {
    try (SqlSession session = factory.openSession()){
      return session.getMapper(DatasetMapper.class).duplicates(minCount, publisherKey);
    }
  }

  private void postDoiDeletionForSources(DatasetSourceMapper psm, int datasetKey){
    psm.listReleaseSources(datasetKey).stream()
      .filter(d -> d.getDoi() != null && d.getDoi().isCOL())
      .forEach(d -> bus.post(DoiChange.delete(d.getDoi())));
  }

  @Override
  protected void deleteBefore(Integer key, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    if (Datasets.COL == key) {
      throw new IllegalArgumentException("You cannot delete the COL project");
    }

    // old is null as we have set offerChangeHook to false - we only need it here so lets call it manually
    old = mapper.get(key);

    // avoid deletions of annual releases of COL
    if (old != null
        && old.getOrigin().isRelease()
        && old.getSourceKey().equals(Datasets.COL)
        && !old.isPrivat()
        && old.getVersion().startsWith("Annual")
    ) {
      throw new IllegalArgumentException("You cannot delete public annual releases of the COL project");
    }

    DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
    if (old != null && old.getOrigin() == DatasetOrigin.PROJECT) {
      // This is a recursive project delete.
      List<Dataset> releases = mapper.listReleases(key);
      LOG.warn("Deleting project {} with all its {} releases", key, releases.size());

      // Simplify the DOI updates by deleting ALL DOIs for ALL releases and ALL sources at the beginning
      LOG.warn("Request deletion of all DOIs from project {}", key);
      postDoiDeletionForSources(psm, key);
      // cascade to releases first before we remove the mother project dataset
      for (var d : releases) {
        LOG.info("Deleting release {} of project {}", d.getKey(), key);
        postDoiDeletionForSources(psm, d.getKey());
        delete(d.getKey(), user);
      }
    }
    // remove source citations
    var cm = session.getMapper(CitationMapper.class);
    cm.delete(key);
    // remove decisions, estimates, dataset patches, archived usages, name matches,
    // but NOT sectors which are referenced from data tables
    for (Class<DatasetProcessable<?>> mClass : new Class[]{
      DecisionMapper.class, EstimateMapper.class, DatasetPatchMapper.class, ArchivedNameUsageMapper.class, NameMatchMapper.class
    }) {
      LOG.info("Delete {}s for dataset {}", mClass.getSimpleName().substring(0, mClass.getSimpleName().length() - 6), key);
      session.getMapper(mClass).deleteByDataset(key);
      session.commit();
    }
    // remove id reports only for private releases - we want to keep public releases forever to track ids!!!
    if (old != null
        && old.getOrigin().isRelease()
        && old.isPrivat()
    ) {
      LOG.info("Delete id reports for private release {}", key);
      session.getMapper(IdReportMapper.class).deleteByDataset(key);
    }
    // request DOI update/deletion for all source DOIs - they might be shared across releases so we cannot just delete them
    Set<DOI> dois = psm.listReleaseSources(key).stream()
        .map(Dataset::getDoi)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    // remove dataset archive & its citations
    LOG.info("Delete dataset archive for dataset {}", key);
    session.getMapper(DatasetArchiveMapper.class).deleteByDataset(key);
    cm.deleteArchive(key);
    // remove import & sync history - we keep them with projects, so deleting a single release will not have any impact on them
    LOG.info("Delete sector sync history for dataset {}", key);
    session.getMapper(SectorImportMapper.class).deleteByDataset(key);
    LOG.info("Delete dataset import history for dataset {}", key);
    session.getMapper(DatasetImportMapper.class).deleteByDataset(key);
    // delete all partitioned data
    deleteData(key, session);
    session.commit();
    // now also remove sectors
    session.getMapper(SectorMapper.class).deleteByDataset(key);
    // now also clear filesystem
    diDao.removeMetrics(key);
    FileUtils.deleteQuietly(nCfg.scratchDir(key));
    FileUtils.deleteQuietly(nCfg.archiveDir(key));
    FileUtils.deleteQuietly(rCfg.reportDir(key));
    // remove stored logos
    imgService.delete(key);
    // remove exports & project sources if dataset was private
    if (old != null && old.isPrivat()) {
      // project source dataset archives & its citations
      LOG.info("Delete archived sources for private dataset {}", key);
      psm.deleteByRelease(key);
      cm.deleteByRelease(key);
      // exports
      if (exportDao == null) {
        LOG.warn("No export dao configured. Cannot delete exports for private dataset {}", key);
      } else {
        LOG.info("Delete exports for private dataset {}", key);
        exportDao.deleteByDataset(key, user);
      }
    }
    // trigger DOI update at the very end for the now removed sources!
    dois.forEach(doi -> bus.post(DoiChange.change(doi)));
  }

  /**
   * Deletes all data from partitioned tables, clears the usage counter and removed sequences if existing
   */
  public void deleteData(int key, SqlSession session) {
    var dpm = session.getMapper(DatasetPartitionMapper.class);
    // delete usage counter record
    dpm.deleteUsageCounter(key);
    // delete data from partitions
    LOG.info("Delete partitioned data for dataset {}", key);
    Lists.reverse(DatasetPartitionMapper.PARTITIONED_TABLES).forEach(t -> dpm.deleteData(t, key));
    DatasetPartitionMapper.IDMAP_TABLES.forEach(t -> dpm.dropTable(t, key));
    // drop id sequences
    dpm.deleteSequences(key);
  }

  @Override
  protected boolean deleteAfter(Integer key, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    // deletion event to post later
    var delEvent = DatasetChanged.deleted(old, user);
    // remember all ACL users in the deletion event so we can properly invalidate user caches
    UserMapper um = session.getMapper(UserMapper.class);
    for (var u : um.datasetEditors(key)) {
      delEvent.usernamesToInvalidate.add(u.getUsername());
    }
    for (var u : um.datasetReviewer(key)) {
      delEvent.usernamesToInvalidate.add(u.getUsername());
    }
    // track who did the deletion in modified_by and remove all access rights
    mapper.clearACL(key, user);

    // physically delete dataset if its temporary
    if (key >= TEMP_KEY_START) {
      mapper.deletePhysically(key);
    }
    session.close();

    // clear search index asynchroneously
    CompletableFuture.supplyAsync(() -> indexService.deleteDataset(key))
      .exceptionally(e -> {
        LOG.error("Failed to delete ES docs for dataset {}", key, e.getCause());
        return 0;
      });
    // notify event bus
    bus.post(delEvent);
    if (old.getDoi() != null && old.getDoi().isCOL()) {
      bus.post(DoiChange.delete(old.getDoi()));
    }
    return false;
  }

  @Override
  public Integer create(Dataset obj, int user) {
    // apply some defaults for required fields
    sanitize(obj);
    return super.create(obj, user);
  }

  public Integer create(DatasetWithSettings obj, int user) {
    var key = create(obj.getDataset(), user);
    if (obj.getSettings() != null) {
      try (SqlSession session = factory.openSession(true)) {
        var dm = session.getMapper(mapperClass);
        dm.updateSettings(key, obj.getSettings(), user);
      }
    }
    return key;
  }

  /**
   * Create a temporary dataset which will be fully deleted and does not use the id sequence of postgresql for its key.
   * Instead we assign a free key in the higher range manually.
   */
  public Integer createTemp(Dataset d, int user) {
    d.applyUser(user);
    d.setKey(tempIds.newKey());

    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(mapperClass);
      dm.createWithID(d);
    }
    return d.getKey();
  }
  @Override
  protected boolean createAfter(Dataset obj, int user, DatasetMapper mapper, SqlSession session) {
    // persist source citations
    if (obj.getSource() != null) {
      var cm = session.getMapper(CitationMapper.class);
      for (var c : obj.getSource()) {
        cm.create(obj.getKey(), c);
      }
    }
    // update alias for publisher based datasets - we need the generated key for it
    if (obj.getAlias() == null && obj.getGbifPublisherKey() != null && iCfg.publisherAlias.containsKey(obj.getGbifPublisherKey())) {
      obj.setAlias(iCfg.publisherAlias.get(obj.getGbifPublisherKey()) + obj.getKey());
      mapper.update(obj);
    }

    // sequences for mutable projects - releases and external datasets do not need persistent sequences. Imports generate them on the fly temporarily
    if (obj.getOrigin() == DatasetOrigin.PROJECT) {
      session.getMapper(DatasetPartitionMapper.class).createSequences(obj.getKey());
    }
    session.commit();
    session.close();
    // other non pg stuff
    pullLogo(obj, null, user);
    bus.post(DatasetChanged.created(obj, user));
    return false;
  }

  public void update(DatasetWithSettings obj, int user) {
    super.update(obj.getDataset(), user);
    putSettings(obj.getKey(), obj.getSettings(), user);
  }

  /**
   * @param old never null as updateHook is enabled!
   */
  @Override
  protected void updateBefore(Dataset obj, @NotNull Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    // changing a private to a public release is only allowed if there is no newer public release already!
    if (obj.getSourceKey() != null && obj.getOrigin().isRelease()
        && old.isPrivat() // was private before
        && !obj.isPrivat() // but now is public
    ) {
      var lr = mapper.latestRelease(obj.getSourceKey(), true, obj.getOrigin());
      // we make use of the fact that datasetKeys are sequential numbers
      if (lr != null && lr > obj.getKey()) {
        throw new IllegalArgumentException("A newer public release already exists. You cannot turn this private release public");
      }
    }
    // copy all required fields which are not patch fields from old copy if missing
    ObjectUtils.setIfNull(obj.getType(), obj::setType, old.getType());
    ObjectUtils.setIfNull(obj.getTitle(), obj::setTitle, old.getTitle());
    ObjectUtils.setIfNull(obj.getLicense(), obj::setLicense, old.getLicense());
    ObjectUtils.setIfNull(obj.getOrigin(), obj::setOrigin, old.getOrigin());
    if (!java.util.Objects.equals(obj.getOrigin(), old.getOrigin())) {
      throw new IllegalArgumentException("origin is immutable and must remain " + old.getOrigin());
    }
    sanitize(obj);
    // if list of creators for a project changes, adjust the max container author settings
    if (obj.getOrigin() == DatasetOrigin.PROJECT && CollectionUtils.size(obj.getCreator()) != CollectionUtils.size(old.getCreator())) {
      var ds = getSettings(obj.getKey());
      ds.put(Setting.SOURCE_MAX_CONTAINER_AUTHORS, CollectionUtils.size(obj.getCreator()));
      putSettings(obj.getKey(), ds, user);
    }
    super.updateBefore(obj, old, user, mapper, session);
  }

  @Override
  protected boolean updateAfter(Dataset obj, Dataset old, int user, DatasetMapper mapper, SqlSession session, boolean keepSessionOpen) {
    // persist source citations if they changed
    if (!java.util.Objects.equals(old.getSource(), obj.getSource())) {
      var cm = session.getMapper(CitationMapper.class);
      // erase and recreate
      cm.delete(obj.getKey());
      if (obj.getSource() != null) {
        for (var c : obj.getSource()) {
          cm.create(obj.getKey(), c);
        }
      }
    }
    session.commit();
    session.close();
    // other non pg stuff
    pullLogo(obj, old, user);
    bus.post(DatasetChanged.changed(obj, old, user));
    if (obj.getDoi() != null && obj.getDoi().isCOL()) {
      bus.post(DoiChange.change(old.getDoi()));
    }
    return false;
  }

  private void pullLogo(Dataset d, Dataset old, int user) {
    if (old == null || !Objects.equal(d.getLogo(), old.getLogo())) {
      LogoUpdateJob.updateDatasetAsync(d, factory, downloader, scratchFileFunc, imgService, user);
    }
  }

  public Dataset copy(int datasetKey, int userKey, BiConsumer<Dataset, DatasetSettings> modifier){
    Dataset copy;

    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      copy = dm.get(datasetKey);
      // create new dataset based on current metadata
      copy.setSourceKey(datasetKey);
      copy.setGbifKey(null);
      copy.setGbifPublisherKey(null);
      copy.setModifiedBy(userKey);
      copy.setCreatedBy(userKey);

      if (modifier != null) {
        DatasetSettings ds = dm.getSettings(datasetKey);
        int before = ds.hashCode();
        modifier.accept(copy, ds);
        // modifier might have changed the settings, persist if so!
        if (ds.hashCode() != before) {
          dm.updateSettings(datasetKey, ds, userKey);
        }
      }
      copy.setKey(null); // make sure we have no key so we create
    }

    create(copy, userKey);

    // copy logo files
    try {
      imgService.copyDatasetLogo(datasetKey, copy.getKey());
    } catch (IOException e) {
      LOG.error("Failed to copy logos from dataset {} to dataset {}", datasetKey, copy.getKey(), e);
    }

    return copy;
  }

  /**
   * Reads a specific copy by its attempt from the dataset metadata archive.
   */
  public Dataset getArchive(Integer key, Integer attempt) {
    try (SqlSession session = factory.openSession()){
      DatasetArchiveMapper dam = session.getMapper(DatasetArchiveMapper.class);
      return dam.get(key, attempt);
    }
  }

  /**
   * Convenience method to publish a dataset.
   * Interally this loads the dataset instance, changes its private value and calls an update which does trigger the publication procedures.
   * @return true if the private flag has changed and the dataset was published
   */
  public boolean publish(int key, User user) {
    try (SqlSession session = factory.openSession(true)){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      var d = dm.get(key);
      if (d == null || d.hasDeletedDate()) {
        throw NotFoundException.notFound(Dataset.class, key);
      }
      if (d.isPrivat()) {
        d.setPrivat(false);
        update(d, user.getKey());
        return true;
      }
      return false;
    }
  }

  public int deleteTempDatasets() {
    List<Integer> toDelete;
    LocalDateTime expiryDate = LocalDateTime.now().minusDays(TEMP_EXPIRY_DAYS);
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      toDelete = dm.keysAbove(TEMP_KEY_START, expiryDate);
    }
    LOG.info("Deleting {} expired temporary datasets...", toDelete.size());
    for (var key : toDelete) {
      delete(key, Users.IMPORTER);
    }
    LOG.info("Deleted {} expired temporary datasets", toDelete.size());
    return toDelete.size();
  }


  private class TempIdProvider {
    private static final int fetchSize = 100;
    private final IntSet keys = new IntOpenHashSet();

    private void loadKeys() {
      LOG.info("Fetching {} unused temporary dataset keys...", fetchSize);
      IntSet used = new IntOpenHashSet();
      try (SqlSession session = factory.openSession(true)) {
        DatasetMapper dm = session.getMapper(DatasetMapper.class);
        for (var k : dm.keysAbove(TEMP_KEY_START, null)) {
          used.add(k);
        }
      }
      int idx = TEMP_KEY_START;
      while (keys.size() < fetchSize) {
        if (!used.contains(idx)) {
          keys.add(idx);
        }
        idx++;
      }
    }

    private synchronized int newKey() {
      if (keys.isEmpty()) {
        loadKeys();
      }
      int k = keys.intIterator().nextInt();
      keys.remove(k);
      return k;
    }
  }
}
