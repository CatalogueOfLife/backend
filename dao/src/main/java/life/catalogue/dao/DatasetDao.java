package life.catalogue.dao;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DoiChange;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Setting;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.text.CitationUtils;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.Validator;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.eventbus.EventBus;

import static life.catalogue.common.util.PrimitiveUtils.intDefault;
import static life.catalogue.metadata.MetadataFactory.stripHtml;

/**
 * A DAO for datasets that orchestrates the needs for partitioning tables and removing dataset remains
 * in other areas, e.g. exports, import metrics, images.
 *
 * The Partitioning of CLB is dataset key based and differs between EXTERNAL datasets that live on the default partition
 * and MANAGED & RELEASED datasets that live on their own, dedicated partition.
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

  private final NormalizerConfig nCfg;
  private final ReleaseConfig rCfg;
  private final DownloadUtil downloader;
  private final ImageService imgService;
  private final BiFunction<Integer, String, File> scratchFileFunc;
  private final DatasetImportDao diDao;
  private final DatasetExportDao exportDao;
  private final NameUsageIndexService indexService;
  private final EventBus bus;
  private final KeyGenerator keyGenerator;

  /**
   * @param scratchFileFunc function to generate a scrach dir for logo updates
   * @param minExternalDatasetKey The lowest dataset key to use for new external datasets.
   */
  public DatasetDao(int minExternalDatasetKey, SqlSessionFactory factory,
                    NormalizerConfig nCfg, ReleaseConfig rCfg,
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
    this.downloader = downloader;
    this.imgService = imgService;
    this.scratchFileFunc = scratchFileFunc;
    this.diDao = diDao;
    this.exportDao = exportDao;
    this.indexService = indexService;
    this.bus = bus;
    // load existing max keys
    this.keyGenerator = new KeyGenerator(minExternalDatasetKey, factory);
  }

  /**
   * For testing only!!!
   * THis is using mocks and misses real functionality, but simplifies the construction of the core dao.
   */
  @VisibleForTesting
  public DatasetDao(SqlSessionFactory factory, DownloadUtil downloader, DatasetImportDao diDao, Validator validator) {
    this(100, factory, new NormalizerConfig(), new ReleaseConfig(), downloader, ImageService.passThru(), diDao, null, NameUsageIndexService.passThru(), null, new EventBus(), validator);
  }

  public static class KeyGenerator {
    public final int minExternalDatasetKey;
    private final AtomicInteger keyGenExternal;
    private final AtomicInteger keyGenProject;

    public KeyGenerator(int minExternalDatasetKey, SqlSessionFactory factory) {
      this(minExternalDatasetKey, 0, 0);
      setMax(factory);
    }

    public KeyGenerator(int minExternalDatasetKey, int maxExternal, int maxProject) {
      this.minExternalDatasetKey = minExternalDatasetKey;
      this.keyGenExternal = new AtomicInteger(maxExternal);
      this.keyGenProject = new AtomicInteger(maxProject);
    }

    public void setMax(SqlSessionFactory factory) {
      try (SqlSession session = factory.openSession(true)) {
        var dm = session.getMapper(DatasetMapper.class);
        int ext = Math.max(minExternalDatasetKey, intDefault(dm.getMaxKey(null), 0));
        int proj = intDefault(dm.getMaxKey(minExternalDatasetKey), 10);
        setMax(ext, proj);
      }
    }

    public void setMax(int maxExternal, int maxProject) {
      this.keyGenExternal.set(maxExternal);
      this.keyGenProject.set(maxProject);
      LOG.info("Set dataset key generator to {} for external and {} for others", keyGenExternal.get(), keyGenProject.get());
    }

    public int nextExternalKey() {
      return keyGenExternal.incrementAndGet();
    }

    public int nextProjectKey() {
      int key = keyGenProject.incrementAndGet();
      if (key >= minExternalDatasetKey) {
        throw new IllegalStateException("Project key range used up. Please reconfigure minExternalDatasetKey");
      }
      if (minExternalDatasetKey - key < 100) {
        LOG.warn("Project key range running low, only {} left. Please reconfigure minExternalDatasetKey", minExternalDatasetKey - key);
      }
      return key;
    }

    public int setKey(Dataset d) {
      if (d.getOrigin().isManagedOrRelease()) {
        d.setKey(nextProjectKey());
      } else {
        d.setKey(nextExternalKey());
      }
      return d.getKey();
    }
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
      if (d.getSource() != null) {
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
        d.getCreator().removeIf(java.util.Objects::isNull);
      }
      if (d.getEditor() != null) {
        d.getEditor().removeIf(java.util.Objects::isNull);
      }
      if (d.getContributor() != null) {
        d.getContributor().removeIf(java.util.Objects::isNull);
      }
      if (d.getDescription() != null) {
        d.setDescription(stripHtml(d.getDescription()));
      }
      if (d.getTitle() != null) {
        d.setTitle(stripHtml(d.getTitle()));
      }
    }
  }

  private static String buildID(Citation c) {
    StringBuilder sb = new StringBuilder();
    if (c.getAuthor() != null && c.getAuthor().size() > 0) {
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
    d.setOrigin(DatasetOrigin.MANAGED);
    d.setIssued(FuzzyDate.now());
    d.setLogo(URI.create("https://gbif.org"));
    d.setUrl(d.getLogo());
    d.setCreated(LocalDateTime.now());
    d.setModified(LocalDateTime.now());
    d.setImported(LocalDateTime.now());
    // try with all templates, throwing IAE if bad
    verifySetting(ds, Setting.RELEASE_ALIAS_TEMPLATE, d, null);
  }

  static void verifySetting(DatasetSettings ds, Setting setting, Dataset d, Dataset d2) throws IllegalArgumentException {
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

  public Dataset latestRelease(int projectKey) {
    try (SqlSession session = factory.openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Integer key = dm.latestRelease(projectKey, true);
      if (key == null) {
        throw new NotFoundException("Dataset " + projectKey + " was never released");
      }
      return dm.get(key);
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
        && old.getOrigin() == DatasetOrigin.RELEASED
        && old.getSourceKey().equals(Datasets.COL)
        && !old.isPrivat()
        && old.getVersion().startsWith("Annual")
    ) {
      throw new IllegalArgumentException("You cannot delete public annual releases of the COL project");
    }

    DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
    if (old != null && old.getOrigin() == DatasetOrigin.MANAGED) {
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
      DecisionMapper.class, EstimateMapper.class, DatasetPatchMapper.class, ArchivedNameMapper.class, NameMatchMapper.class
    }) {
      LOG.info("Delete {}s for dataset {}", mClass.getSimpleName().substring(0, mClass.getSimpleName().length() - 6), key);
      session.getMapper(mClass).deleteByDataset(key);
      session.commit();
    }
    // remove id reports only for private releases - we want to keep public releases forever to track ids!!!
    if (old != null
        && old.getOrigin() == DatasetOrigin.RELEASED
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
    // delete data partitions
    if (old != null) {
      Partitioner.delete(session, key, old.getOrigin());
    }
    session.commit();
    // now also remove sectors
    session.getMapper(SectorMapper.class).deleteByDataset(key);
      // drop managed id sequences
    session.getMapper(DatasetPartitionMapper.class).deleteManagedSequences(key);
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
      LOG.info("Delete exports for private dataset {}", key);
      exportDao.deleteByDataset(key, user);
    }
    // trigger DOI update at the very end for the now removed sources!
    dois.forEach(doi -> bus.post(DoiChange.change(doi)));
  }

  @Override
  protected boolean deleteAfter(Integer key, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    // track who did the deletion in modified_by
    mapper.updateModifiedBy(key, user);
    session.close();
    // clear search index asynchroneously
    CompletableFuture.supplyAsync(() -> indexService.deleteDataset(key))
      .exceptionally(e -> {
        LOG.error("Failed to delete ES docs for dataset {}", key, e.getCause());
        return 0;
      });
    // notify event bus
    bus.post(DatasetChanged.deleted(old));
    if (old.getDoi() != null && old.getDoi().isCOL()) {
      bus.post(DoiChange.delete(old.getDoi()));
    }
    return false;
  }

  @Override
  public Integer create(Dataset obj, int user) {
    // apply some defaults for required fields
    sanitize(obj);
    // generate key depending on origin!
    keyGenerator.setKey(obj);
    return super.create(obj, user);
  }

  public Integer create(DatasetWithSettings obj, int user) {
    var key = create(obj.getDataset(), user);
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(mapperClass);
      dm.updateSettings(key, obj.getSettings(), user);
    }
    return key;
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
    // data partitions
    if (obj.getOrigin() == DatasetOrigin.MANAGED) {
      recreatePartition(obj.getKey(), obj.getOrigin());
    }
    session.commit();
    session.close();
    // other non pg stuff
    pullLogo(obj, null, user);
    bus.post(DatasetChanged.created(obj));
    return false;
  }

  /**
   * @param old never null as updateHook is enabled!
   */
  @Override
  protected void updateBefore(Dataset obj, @NotNull Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    // changing a private to a public release is only allowed if there is no newer public release already!
    if (obj.getSourceKey() != null && obj.getOrigin() == DatasetOrigin.RELEASED
        && old.isPrivat() // was private before
        && !obj.isPrivat() // but now is public
    ) {
      var lr = mapper.latestRelease(obj.getSourceKey(), true);
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
    if (obj.getOrigin() == DatasetOrigin.MANAGED && CollectionUtils.size(obj.getCreator()) != CollectionUtils.size(old.getCreator())) {
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
    // data partitions
    if (obj.getOrigin() == DatasetOrigin.MANAGED && !session.getMapper(DatasetPartitionMapper.class).exists(obj.getKey(), obj.getOrigin())) {
      // suspicious. Should there ever be a managed dataset without partitions?
      recreatePartition(obj.getKey(), obj.getOrigin());
    }
    session.commit();
    session.close();
    // other non pg stuff
    pullLogo(obj, old, user);
    bus.post(DatasetChanged.changed(obj, old));
    if (obj.getDoi() != null && obj.getDoi().isCOL()) {
      bus.post(DoiChange.change(old.getDoi()));
    }
    return false;
  }

  private void recreatePartition(int datasetKey, DatasetOrigin origin) {
    Partitioner.partition(factory, datasetKey, origin);
    Partitioner.attach(factory, datasetKey, origin);
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
}
