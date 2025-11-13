package life.catalogue.dao;

import life.catalogue.api.event.ChangeDoi;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.event.EventBroker;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;

import static life.catalogue.common.text.StringUtils.removePunctWS;
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
  public static final int TEMP_KEY_START = 100_000_000;
  private final NormalizerConfig nCfg;
  private final ReleaseConfig rCfg;
  private final DownloadUtil downloader;
  private final ImageService imgService;
  private final BiFunction<Integer, String, File> scratchFileFunc;
  private final DatasetImportDao diDao;
  private final DatasetExportDao exportDao;
  private final NameUsageIndexService indexService;
  private final EventBroker bus;
  private final TempIdProvider tempIds;
  private final URI gbifDatasetApi;


  /**
   * @param scratchFileFunc function to generate a scrach dir for logo updates
   */
  public DatasetDao(SqlSessionFactory factory,
                    NormalizerConfig nCfg, ReleaseConfig rCfg, GbifConfig gbifCfg,
                    DownloadUtil downloader,
                    ImageService imgService,
                    DatasetImportDao diDao,
                    DatasetExportDao exportDao,
                    NameUsageIndexService indexService,
                    BiFunction<Integer, String, File> scratchFileFunc,
                    EventBroker bus,
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
    this.tempIds = new TempIdProvider();
    this.gbifDatasetApi = URI.create(gbifCfg.api).resolve("dataset/");
  }

  /**
   * For testing only!!!
   * THis is using mocks and misses real functionality, but simplifies the construction of the core dao.
   */
  @VisibleForTesting
  public DatasetDao(SqlSessionFactory factory, DownloadUtil downloader, DatasetImportDao diDao, Validator validator, EventBroker broker) {
    this(factory, new NormalizerConfig(), new ReleaseConfig(), new GbifConfig(), downloader, ImageService.passThru(), diDao, null, NameUsageIndexService.passThru(), null, broker, validator);
  }

  public static boolean isTempKey(Integer key) {
    return key != null && key >= TEMP_KEY_START;
  }

  public Dataset get(UUID gbifKey) {
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(mapperClass);
      return mapper.getByGBIF(gbifKey);
    }
  }

  public DatasetRelease getRelease(Integer key) {
    try (SqlSession session = factory.openSession()) {
      var mapper = session.getMapper(mapperClass);
      return mapper.getRelease(key);
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
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateSettings(key, settings, userKey);
      session.commit();
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
    psm.listReleaseSourcesSimple(datasetKey, false).stream()
      .filter(d -> d.getDoi() != null && d.getDoi().isCOL())
      .forEach(d -> bus.publish(ChangeDoi.delete(d.getDoi())));
  }

  /**
   * Method to completely delete all information related to a dataset.
   * Should be used only to completely delete all releases when a project gets deleted.
   * @param key
   * @param user
   */
  private void deleteEntirely(int key, int user) {
    delete(key, user);
    // now also delete things we usually keep for published releases
    try (SqlSession session = factory.openSession(true)) {
      deleteKeptReleaseData(key, session);
    }
  }

  /**
   * Remove the data we keep for deleted, public releases
   */
  private void deleteKeptReleaseData(int key, SqlSession session) {
    session.getMapper(SectorMapper.class).deleteByDataset(key);
    session.getMapper(SectorPublisherMapper.class).deleteByDataset(key);
    session.getMapper(CitationMapper.class).deleteByRelease(key);
    session.getMapper(DatasetSourceMapper.class).deleteByRelease(key);
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
        && java.util.Objects.equals(old.getSourceKey(), Datasets.COL)
        && !old.isPrivat()
        && old.getVersion().contains("Annual")
    ) {
      throw new IllegalArgumentException("You cannot delete public annual releases of the COL project");
    }

    DatasetSourceMapper dsm = session.getMapper(DatasetSourceMapper.class);
    if (old != null && old.getOrigin() == DatasetOrigin.PROJECT) {
      // This is a recursive project delete.
      List<Dataset> releases = mapper.listReleases(key);
      LOG.warn("Deleting project {} with all its {} releases and source information!", key, releases.size());

      // Simplify the DOI updates by deleting ALL DOIs for ALL releases and ALL sources at the beginning
      LOG.warn("Request deletion of all DOIs from project {}", key);
      postDoiDeletionForSources(dsm, key);
      // cascade to releases first before we remove the mother project dataset
      for (var d : releases) {
        LOG.info("Deleting release {} of project {}", d.getKey(), key);
        postDoiDeletionForSources(dsm, d.getKey());
        deleteEntirely(d.getKey(), user);
      }
    } else if (old != null) {
      LOG.info("Delete {} dataset {}: {}", old.getOrigin(), key, old.getTitle());
    } else {
      LOG.info("Delete dataset {}", key);
    }

    // remove source citations
    var cm = session.getMapper(CitationMapper.class);
    cm.delete(key);
    // remove decisions, estimates, dataset patches, archived usages, name matches,
    // but NOT sectors or sector_publisher which are referenced from data tables and which we want to keep for public release
    for (Class<DatasetProcessable<?>> mClass : new Class[]{
      DecisionMapper.class, EstimateMapper.class, DatasetPatchMapper.class, ArchivedNameUsageMapper.class, NameMatchMapper.class
    }) {
      LOG.info("Delete {}s for dataset {}", mClass.getSimpleName().substring(0, mClass.getSimpleName().length() - 6), key);
      session.getMapper(mClass).deleteByDataset(key);
      session.commit();
    }
    // request DOI update/deletion for all source DOIs - they might be shared across releases so we cannot just delete them
    Set<DOI> dois = dsm.listReleaseSourcesSimple(key, false).stream()
        .map(DatasetSourceMapper.SourceDataset::getDoi)
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
    // now also remove sectors, unless it was a published release.
    // We want to keep the sector and sector_publisher entries for deleted, public release !!!
    if (old == null || old.isPrivat() || old.getOrigin() == DatasetOrigin.PROJECT) {
      session.getMapper(SectorMapper.class).deleteByDataset(key);
      session.getMapper(SectorPublisherMapper.class).deleteByDataset(key);
    }
    // now also clear filesystem - again release metrics are stored with the project so this is safe
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
      dsm.deleteByRelease(key);
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
    dois.forEach(doi -> bus.publish(ChangeDoi.change(doi)));
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
      LOG.info("Physically delete temporary dataset {}", key);
      deleteKeptReleaseData(key, session);
      mapper.deletePhysically(key);
    }
    session.commit();
    session.close();

    // clear search index asynchroneously
    CompletableFuture.supplyAsync(() -> indexService.deleteDataset(key))
      .exceptionally(e -> {
        LOG.error("Failed to delete ES docs for dataset {}", key, e.getCause());
        return 0;
      });
    // notify event bus
    bus.publish(delEvent);
    if (old.getDoi() != null && old.getDoi().isCOL()) {
      bus.publish(ChangeDoi.delete(old.getDoi()));
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
    return create(obj.getDataset(), obj.getSettings(), user);
  }

  public Integer create(Dataset d, DatasetSettings settings, int user) {
    var key = create(d, user);
    if (settings != null) {
      try (SqlSession session = factory.openSession(true)) {
        var dm = session.getMapper(mapperClass);
        dm.updateSettings(key, settings, user);
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
    // update alias for ARTICLE datasets: https://github.com/CatalogueOfLife/backend/issues/1421
    if (obj.getAlias() == null && obj.getType() == DatasetType.ARTICLE) {
      obj.setAlias(articleAlias(obj));
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
    bus.publish(DatasetChanged.created(obj, user));
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
    // update alias for ARTICLE datasets when none existed: https://github.com/CatalogueOfLife/backend/issues/1421
    if (obj.getAlias() == null && obj.getType() == DatasetType.ARTICLE) {
      obj.setAlias(articleAlias(obj));
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

  static String articleAlias(Dataset d) {
    StringBuilder sb = new StringBuilder();
    if (d.getSource() != null && d.getSource().size() == 1 &&
      d.getSource().get(0).getAuthor() != null && !d.getSource().get(0).getAuthor().isEmpty() && !StringUtils.isBlank(d.getSource().get(0).getAuthor().get(0).getFamily())) {
      var src = d.getSource().get(0);
      var author = src.getAuthor().get(0);
      if (!StringUtils.isBlank(author.getNonDroppingParticle())) {
        sb.append((author.getNonDroppingParticle()));
      }
      sb.append(author.getFamily());
      if (src.getIssued() != null) {
        sb.append(src.getIssued().getYear());
      }

    } else if (d.getCreator() != null && !d.getCreator().isEmpty()) {
      var author = d.getCreator().get(0);
      var name = ObjectUtils.coalesce(author.getFamily(), author.getOrganisation(), author.getName());
      if (!StringUtils.isBlank(name)) {
        sb.append(name);
      }
      if (d.getIssued() != null) {
        sb.append(d.getIssued().getYear());
      }
    }
    return sb.length() > 2 ? removePunctWS(sb.toString()) : null;
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
    bus.publish(DatasetChanged.changed(obj, old, user));
    if (obj.getDoi() != null && obj.getDoi().isCOL()) {
      bus.publish(ChangeDoi.change(old.getDoi()));
    }
    return false;
  }

  private void pullLogo(Dataset d, Dataset old, int user) {
    if (old == null || !Objects.equal(d.getLogo(), old.getLogo())) {
      LogoUpdateJob.updateDatasetAsync(d, factory, downloader, scratchFileFunc, imgService, user);
    }
  }

  public Dataset copy(int datasetKey, int userKey, Consumer<Dataset> modifier){
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
        modifier.accept(copy);
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

  public NameUsageIndexService getIndexService() {
    return indexService;
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
   * Internally this loads the dataset instance, changes its private value and calls an update which does trigger the publication procedures.
   * @return true if the private flag has changed and the dataset was published
   */
  public boolean publish(int key, User user) {
    var d = getNotDeleted(key);
    if (d.isPrivat()) {
      d.setPrivat(false);
      update(d, user.getKey());
      return true;
    }
    return false;
  }

  /**
   * Convenience method to make a dataset private again.
   * Internally this loads the dataset instance, changes its private value and calls an update which does trigger the publication procedures.
   * @return true if the private flag has changed and the dataset was unpublished
   */
  public boolean unpublish(int key, User user) {
    var d = getNotDeleted(key);
    if (!d.isPrivat()) {
      d.setPrivat(true);
      update(d, user.getKey());
      return true;
    }
    return false;
  }

  private Dataset getNotDeleted(int key) {
    try (SqlSession session = factory.openSession(true)){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      var d = dm.get(key);
      if (d == null || d.hasDeletedDate()) {
        throw NotFoundException.notFound(Dataset.class, key);
      }
      return d;
    }
  }

  public int deleteTempDatasets(@Nullable LocalDateTime expiryDate) {
    List<Integer> toDelete;
    LOG.info("Looking for temporary datasets to be removed and created before {}", expiryDate);
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

  public Stream<Object[]> listDeletedInGBIF() {
    List<DatasetGBIF> ds;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      ds = dm.listGBIF();
    }
    return ds.stream()
      .filter(d -> deletedInGBIF(d))
      .map(this::map2Rows);
  }

  private Object[] map2Rows(DatasetGBIF d) {
    return new Object[]{d.getKey(), d.getGbifKey(), d.getGbifPublisherKey(), d.getAlias(), d.getTitle()};
  }

  private boolean deletedInGBIF(DatasetGBIF d) {
    // skip all plazi datasets as there are far too many
    // Plazi also registers first with CLB, so it is more difficult to evaluate if a dataset was truely deleted in GBIF
    if (java.util.Objects.equals(d.getGbifPublisherKey(), Publishers.PLAZI)) {
      return false;
    }
    try {
      var req = new HttpHead(gbifDatasetApi.resolve(d.getGbifKey().toString()));
      int code = downloader.getClient().execute(req, HttpResponse::getCode);
      if (code != 200) {
        LOG.info("GBIF Dataset {} with non OK API response: {}", d.getGbifKey(), code);
      }
      return code == HttpStatus.SC_NOT_FOUND;

    } catch (IOException e) {
      LOG.warn("Unable to lookup GBIF dataset {}", d, e);
      // we rather report this dataset as existing to not remove sth because of API outages
      throw new RuntimeException("Unable to lookup GBIF dataset " + d, e);
    }
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
