package life.catalogue.release;

import java.io.IOException;
import java.time.LocalDate;
import java.util.function.Consumer;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.CRUD;
import life.catalogue.db.mapper.*;
import life.catalogue.es.name.index.NameUsageIndexService;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private final Dataset release;
  private final life.catalogue.release.Logger logger = new life.catalogue.release.Logger(LOG);
  // we only allow a single release to run at a time
  private static boolean LOCK = false;
  
  private CatalogueRelease(SqlSessionFactory factory, NameUsageIndexService indexService, AcExporter exporter, DatasetImportDao diDao, int sourceDatasetKey, Dataset release, int userKey) {
    this.factory = factory;
    this.indexService = indexService;
    this.diDao = diDao;
    this.exporter = exporter;
    this.sourceDatasetKey = sourceDatasetKey;
    this.release = release;
    releaseKey = release.getKey();
    this.user = userKey;
  }
  
  /**
   * Release the catalogue into a new dataset
   * @param catalogueKey the draft catalogue to be released, e.g. 3 for the CoL draft
   */
  public static CatalogueRelease release(SqlSessionFactory factory, NameUsageIndexService indexService, AcExporter exporter, DatasetImportDao diDao, int catalogueKey, int userKey) {
    if (!aquireLock()) {
      throw new IllegalStateException("There is a running release already");
    }
    Dataset release;
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      LocalDate today = LocalDate.now();
      // create new dataset based on current metadata
      release = dm.get(catalogueKey);
      release.setKey(null);
      release.setType(DatasetType.CATALOGUE);
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
  
  /**
   * @return The new released datasetKey
   */
  @Override
  public void run() {
    try {
      logger.log("Release catalogue "+sourceDatasetKey+" to new dataset" + releaseKey);
      // prepare new tables
      Partitioner.partition(factory, releaseKey);
      // map ids
      mapIds();
      // copy data
      copyData();
      // build indices and attach partition
      Partitioner.indexAndAttach(factory, releaseKey);
      // create metrics
      metrics();
      // ac-export
      export();
      // ES index
      index();
      
    } catch (IOException e) {
      logger.log("Error releasing catalogue " + sourceDatasetKey + " into dataset " + releaseKey);
      LOG.error("Error releasing catalogue {} into dataset {}", sourceDatasetKey, releaseKey, e);
    } finally {
      releaseLock();
    }
  }
  
  private void index() {
    logger.log("Build search index for catalogue " + releaseKey);
    indexService.indexDataset(releaseKey);
  }
  
  private void metrics() {
    interruptIfCancelled();
    logger.log("Build import metrics for catalogue " +  releaseKey);
    DatasetImport di = diDao.create(release);
    di.setState(ImportState.FINISHED);
    diDao.updateMetrics(di);
    logger.log("Created new import metrics for dataset " + releaseKey + ", attempt " + di.getAttempt());
  }
  
  private void mapIds() {
    interruptIfCancelled();
    logger.log("Map IDs");
    //TODO: match & generate ids
  }
  
  private void copyData() {
    interruptIfCancelled();
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
      dm.process(null, sourceDatasetKey, new ResultHandler<Dataset>() {
        @Override
        public void handleResult(ResultContext<? extends Dataset> ctxt) {
          Dataset d = ctxt.getResultObject();
          LOG.debug("Archive dataset {}: {}", d.getKey(), d.getTitle());
          dm.createArchive(d.getKey(), releaseKey);
        }
      });
    }
  }
  
  private <K, V extends DataEntity<K>, M extends CRUD<K, V> & ProcessableDataset<V>> void copyTable(Class<M> mapperClass, Class<V> entity, Consumer<V> updater) {
    interruptIfCancelled();
    try (SqlSession session = factory.openSession(false);
         TableCopyHandler<K, V,M> handler = new TableCopyHandler<>(factory, entity.getSimpleName(), mapperClass, updater)
    ) {
      logger.log("Copy all " + entity.getSimpleName());
      ProcessableDataset<V> mapper = session.getMapper(mapperClass);
      mapper.processDataset(sourceDatasetKey, handler);
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
      mapper.processDataset(sourceDatasetKey, handler);
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
  private <T extends DataEntity<Integer> & DatasetScoped> void updateEntity(T obj) {
    obj.setKey(null);
    obj.setDatasetKey(releaseKey);
  }
  
  public void export() throws IOException {
    interruptIfCancelled();
    try {
      exporter.export(releaseKey, logger);
    } catch (Throwable e) {
      LOG.error("Error exporting catalogue {}", releaseKey, e);
      logger.log("\n\nERROR!");
      if (e.getMessage() != null) {
        logger.log(e.getMessage());
      }
    }
  }
  
}
