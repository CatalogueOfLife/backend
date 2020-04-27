package life.catalogue.release;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Frequency;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.function.Consumer;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

public class CatalogueRelease implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(CatalogueRelease.class);
  
  private final SqlSessionFactory factory;
  private final DatasetImportDao diDao;
  private final NameUsageIndexService indexService;
  private final AcExporter exporter;
  private final int user;
  private final int sourceDatasetKey;
  private final int releaseKey;
  private final DatasetImport metrics;
  @Deprecated
  private final life.catalogue.release.Logger logger = new life.catalogue.release.Logger(LOG);
  // we only allow a single release to run at a time
  private static boolean LOCK = false;
  
  private CatalogueRelease(SqlSessionFactory factory, NameUsageIndexService indexService, AcExporter exporter, DatasetImportDao diDao, int sourceDatasetKey, Dataset release, int userKey) {
    this.factory = factory;
    this.indexService = indexService;
    this.diDao = diDao;
    this.exporter = exporter;
    this.sourceDatasetKey = sourceDatasetKey;
    metrics = diDao.createWaiting(release, userKey);
    releaseKey = release.getKey();
    this.user = userKey;
  }
  
  /**
   * Release the catalogue into a new dataset
   * @param catalogueKey the draft catalogue to be released, e.g. 3 for the CoL draft
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public static CatalogueRelease release(SqlSessionFactory factory, NameUsageIndexService indexService, AcExporter exporter, DatasetImportDao diDao, int catalogueKey, int userKey) {
    if (!aquireLock()) {
      throw new IllegalStateException("There is a running release already");
    }
    Dataset release;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      // create new dataset based on current metadata
      release = dm.get(catalogueKey);
      if (release.getOrigin() != DatasetOrigin.MANAGED) {
        throw new IllegalArgumentException("Only managed datasets can be released, but origin is " + release.getOrigin());
      }
      
      LocalDate today = LocalDate.now();
      release.setKey(null);
      release.setSourceKey(catalogueKey);
      release.setOrigin(DatasetOrigin.RELEASED);
      release.setAlias(null);
      release.setLocked(true);
      release.setImportFrequency(Frequency.NEVER);
      release.setModifiedBy(userKey);
      release.setCreatedBy(userKey);
      release.setReleased(today);
      release.setVersion(today.toString());
      release.setCitation(buildCitation(release));
      dm.create(release);
      return new CatalogueRelease(factory, indexService, exporter, diDao, catalogueKey, release, userKey);
  
    } catch (Exception e) {
      LOG.error("Error creating release for catalogue {}", catalogueKey, e);
      releaseLock();
      throw new RuntimeException(e);
    }
  }
  
  private static synchronized boolean aquireLock(){
    if (!LOCK) {
      LOCK = true;
      return true;
    }
    return false;
  }
  
  private static synchronized void releaseLock(){
    LOCK = false;
  }
  
  public int getReleaseKey() {
    return releaseKey;
  }
  
  public int getSourceDatasetKey() {
    return sourceDatasetKey;
  }

  public DatasetImport getMetrics() {
    return metrics;
  }

  @Deprecated
  public String getState() {
    return logger.toString();
  }
  
  @VisibleForTesting
  protected static String buildCitation(Dataset d){
    // ${d.authorsAndEditors?join(", ")}, eds. (${d.released.format('yyyy')}). ${d.title}, ${d.released.format('yyyy-MM-dd')}. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.
    StringBuilder sb = new StringBuilder();
    for (String au : d.getAuthorsAndEditors()) {
      if (sb.length() > 1) {
        sb.append(", ");
      }
      sb.append(au);
    }
    sb.append(" (")
      .append(d.getReleased().getYear())
      .append("). ")
      .append(d.getTitle())
      .append(", ")
      .append(d.getReleased().toString())
      .append(". Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.");
    return sb.toString();
  }

  @Override
  public void run() {
    LoggingUtils.setDatasetMDC(releaseKey, getClass());
    try {
      logger.log("Release catalogue "+sourceDatasetKey+" to new dataset" + releaseKey);
      // prepare new tables
      updateState(ImportState.PROCESSING);
      Partitioner.partition(factory, releaseKey);
      // map ids
      updateState(ImportState.MATCHING);
      mapIds();
      // copy data
      updateState(ImportState.INSERTING);
      copyData();
      // build indices and attach partition
      Partitioner.indexAndAttach(factory, releaseKey);
      // create metrics
      updateState(ImportState.BUILDING_METRICS);
      metrics();
      // ac-export
      updateState(ImportState.EXPORTING);
      export();
      try {
        // ES index
        updateState(ImportState.INDEXING);
        index();
      } catch (RuntimeException e) {
        // allow indexing to fail - sth we can do afterwards again
        LOG.error("Error indexing released & exported dataset {} into ES. Source catalogue={}", releaseKey, sourceDatasetKey, e);
      }
      updateState(ImportState.RELEASED);

      logger.log("Successfully finished releasing catalogue " + sourceDatasetKey);

    } catch (Exception e) {

      metrics.setError(Exceptions.getFirstMessage(e));
      updateState(ImportState.FAILED);
      LOG.error("Error releasing catalogue {} into dataset {}", sourceDatasetKey, releaseKey, e);
      // cleanup partion which probably is not even attached
      Partitioner.delete(factory, releaseKey);

    } finally {
      releaseLock();
      LoggingUtils.removeDatasetMDC();
    }
  }

  private void updateState(ImportState state) {
    interruptIfCancelled();
    metrics.setState(state);
    diDao.update(metrics);
  }

  private void index() {
    logger.log("Build search index for catalogue " + releaseKey);
    indexService.indexDataset(releaseKey);
  }
  
  private void metrics() {
    logger.log("Build import metrics for catalogue " +  releaseKey);
    diDao.updateMetrics(metrics);
    diDao.update(metrics);
    diDao.updateDatasetLastAttempt(metrics);
  }
  
  private void mapIds() {
    logger.log("Map IDs");
    //TODO: match & generate ids
  }
  
  private void copyData() {
    copyTable(SectorMapper.class, Sector.class, this::updateEntity);
    copyTable(ReferenceMapper.class, Reference.class, this::updateDatasetID);
    copyTable(NameMapper.class, Name.class, this::updateDatasetID);
    copyTable(NameRelationMapper.class, NameRelation.class, this::updateEntity);
    copyTable(TaxonMapper.class, Taxon.class, this::updateDatasetID);
    copyTable(SynonymMapper.class, Synonym.class, this::updateDatasetID);
    
    copyExtTable(VernacularNameMapper.class, VernacularName.class, this::updateEntity);
    copyExtTable(DistributionMapper.class, Distribution.class, this::updateEntity);
    copyTable(EstimateMapper.class, SpeciesEstimate.class, this::updateEntity);
    // archive dataset metadata
    try (SqlSession session = factory.openSession(false)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.process(null, sourceDatasetKey).forEach(d -> {
        LOG.debug("Archive dataset {}: {}", d.getKey(), d.getTitle());
        dm.createArchive(d.getKey(), releaseKey);
      });
    }
  }
  
  private <K, V extends DataEntity<K>, M extends CRUD<K, V> & DatasetProcessable<V>> void copyTable(Class<M> mapperClass, Class<V> entity, Consumer<V> updater) {
    interruptIfCancelled();
    try (SqlSession session = factory.openSession(false);
         TableCopyHandler<K, V,M> handler = new TableCopyHandler<>(factory, entity.getSimpleName(), mapperClass, updater)
    ) {
      logger.log("Copy all " + entity.getSimpleName());
      DatasetProcessable<V> mapper = session.getMapper(mapperClass);
      mapper.processDataset(sourceDatasetKey).forEach(handler);
      logger.log("Copied " + handler.getCounter() +" "+ entity.getSimpleName());
    }
  }
  
  private <V extends DatasetScopedEntity<Integer>> void copyExtTable(Class<? extends TaxonExtensionMapper<V>> mapperClass, Class<V> entity, Consumer<TaxonExtension<V>> updater) {
    interruptIfCancelled();
    try (SqlSession session = factory.openSession(false);
         ExtTableCopyHandler<V> handler = new ExtTableCopyHandler<V>(factory, entity.getSimpleName(), mapperClass, updater)
    ) {
      logger.log("Copy all " + entity.getSimpleName());
      TaxonExtensionMapper<V> mapper = session.getMapper(mapperClass);
      mapper.processDataset(sourceDatasetKey).forEach(handler);
      logger.log("Copied " + handler.getCounter() +" "+ entity.getSimpleName());
    }
  }
  
  private <C extends DSID & VerbatimEntity> void updateDatasetID(C obj) {
    obj.setDatasetKey(releaseKey);
    obj.setVerbatimKey(null);
  }
  private <E extends DatasetScopedEntity<Integer> & VerbatimEntity> void updateEntity(TaxonExtension<E> obj) {
    obj.getObj().setId(null);
    obj.getObj().setDatasetKey(releaseKey);
    obj.getObj().setVerbatimKey(null);
  }
  private void updateEntity(NameRelation obj) {
    obj.setId(null);
    obj.setDatasetKey(releaseKey);
    obj.setVerbatimKey(null);
  }
  private <T extends DatasetScopedEntity<Integer>> void updateEntity(T obj) {
    obj.setKey(null);
    obj.setDatasetKey(releaseKey);
  }
  
  public void export() throws IOException {
    try {
      exporter.export(releaseKey);
    } catch (Throwable e) {
      LOG.error("Error exporting catalogue {}", releaseKey, e);
      logger.log("\n\nERROR!");
      if (e.getMessage() != null) {
        logger.log(e.getMessage());
      }
    }
  }
  
}
