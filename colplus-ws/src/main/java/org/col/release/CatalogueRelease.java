package org.col.release;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.DatasetType;
import org.col.dao.DatasetImportDao;
import org.col.dao.Partitioner;
import org.col.db.CRUD;
import org.col.db.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.common.lang.Exceptions.interruptIfCancelled;

public class CatalogueRelease implements Callable<Integer> {
  private static final Logger LOG = LoggerFactory.getLogger(CatalogueRelease.class);
  
  private final SqlSessionFactory factory;
  private final DatasetImportDao diDao;
  private final int user;
  private final int sourceDatasetKey;
  private final int releaseKey;
  private final Dataset release;
  
  public CatalogueRelease(SqlSessionFactory factory, DatasetImportDao diDao, int sourceDatasetKey, Dataset release, int userKey) {
    this.factory = factory;
    this.diDao = diDao;
    this.sourceDatasetKey = sourceDatasetKey;
    this.release = release;
    releaseKey = release.getKey();
    this.user = userKey;
  }
  
  /**
   * Release the catalogue into a new dataset
   * @param catalogueKey the draft catalogue to be released, e.g. 3 for the CoL draft
   */
  public static CatalogueRelease release(SqlSessionFactory factory, DatasetImportDao diDao, int catalogueKey, int userKey) {
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
      release.setTitle(release.getTitle() + " - released " + today);
      release.setReleased(today);
      //TODO: create new release dataset
      dm.create(release);
    }
    return new CatalogueRelease(factory, diDao, catalogueKey, release, userKey);
  }
  
  /**
   * @return The new released datasetKey
   */
  @Override
  public Integer call() throws Exception {
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
    return releaseKey;
  }
  
  private void metrics() {
    interruptIfCancelled();
    diDao.generateMetrics(releaseKey);
  }
  
  private void mapIds() {
    interruptIfCancelled();
    //TODO:
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
  }
  
  private <K, V extends DataEntity<K>, M extends CRUD<K, V> & ProcessableDataset<V>> void copyTable(Class<M> mapperClass, Class<V> entity, Consumer<V> updater) {
    interruptIfCancelled();
    try (SqlSession session = factory.openSession(false);
         TableCopyHandler<K, V,M> handler = new TableCopyHandler<>(factory, entity.getSimpleName(), mapperClass, updater)
    ) {
      LOG.info("Copy all {}", entity.getSimpleName());
      ProcessableDataset<V> mapper = session.getMapper(mapperClass);
      mapper.processDataset(sourceDatasetKey, handler);
      LOG.info("Copied {} {}", handler.getCounter(), entity.getSimpleName());
    }
  }
  
  private <V extends DatasetScopedEntity<Integer>> void copyExtTable(Class<? extends TaxonExtensionMapper<V>> mapperClass, Class<V> entity, Consumer<TaxonExtension<V>> updater) {
    interruptIfCancelled();
    try (SqlSession session = factory.openSession(false);
         ExtTableCopyHandler<V> handler = new ExtTableCopyHandler<V>(factory, entity.getSimpleName(), mapperClass, updater)
    ) {
      LOG.info("Copy all {}", entity.getSimpleName());
      TaxonExtensionMapper<V> mapper = session.getMapper(mapperClass);
      mapper.processDataset(sourceDatasetKey, handler);
      LOG.info("Copied {} {}", handler.getCounter(), entity.getSimpleName());
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
}
