package org.col.release;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.api.vocab.DatasetType;
import org.col.dao.DatasetImportDao;
import org.col.db.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    //partition(factory, releaseDatasetKey);
    // map ids
    mapIds();
    // copy data
    copyData();
    // create metrics
    metrics();
    return releaseKey;
  }
  
  private void metrics() {
    diDao.generateMetrics(releaseKey);
  }
  
  private void mapIds() {
    //TODO:
  }
  
  private void copyData() {
    //copyTable(VerbatimRecordMapper);
    copyTable(ReferenceMapper.class, Reference.class, x -> {});
    copyTable(NameMapper.class, Name.class, x -> {});
    copyTable(TaxonMapper.class, Taxon.class, x -> {});
    copyTable(SynonymMapper.class, Synonym.class, x -> {});
    
    copyExtTable(VernacularNameMapper.class, VernacularName.class, x -> {});
    copyExtTable(DistributionMapper.class, Distribution.class, x -> {});
  }
  
  private <T> void copyTable(Class<? extends DatasetCopyMapper<T>> mapperClass, Class<T> entity, Consumer<T> updater) {
    try (SqlSession session = factory.openSession(false);
         TableCopyHandler<T> handler = new TableCopyHandler<T>(factory, entity.getSimpleName(), mapperClass, updater)
    ) {
      LOG.info("Copy all {}", entity.getSimpleName());
      DatasetCopyMapper<T> mapper = session.getMapper(mapperClass);
      mapper.processDataset(sourceDatasetKey, handler);
      LOG.info("Copied {} {}", handler.getCounter(), entity.getSimpleName());
    }
  }
  
  private <T extends GlobalEntity> void copyExtTable(Class<? extends TaxonExtensionMapper<T>> mapperClass, Class<T> entity, Consumer<TaxonExtension<T>> updater) {
    try (SqlSession session = factory.openSession(false);
         ExtTableCopyHandler<T> handler = new ExtTableCopyHandler<T>(factory, entity.getSimpleName(), mapperClass, updater, releaseKey)
    ) {
      LOG.info("Copy all {}", entity.getSimpleName());
      TaxonExtensionMapper<T> mapper = session.getMapper(mapperClass);
      mapper.processDataset(sourceDatasetKey, handler);
      LOG.info("Copied {} {}", handler.getCounter(), entity.getSimpleName());
    }
  }
  
}
