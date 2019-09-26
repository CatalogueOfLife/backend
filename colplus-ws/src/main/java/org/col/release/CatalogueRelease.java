package org.col.release;

import java.util.concurrent.Callable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Dataset;
import org.col.api.vocab.DatasetType;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CatalogueRelease implements Callable<Integer> {
  private static final Logger LOG = LoggerFactory.getLogger(CatalogueRelease.class);
  
  private final SqlSessionFactory factory;
  private final int sourceDatasetKey;
  private final int releaseDatasetKey;
  
  public CatalogueRelease(SqlSessionFactory factory, int sourceDatasetKey, int releaseDatasetKey) {
    this.factory = factory;
    this.sourceDatasetKey = sourceDatasetKey;
    this.releaseDatasetKey = releaseDatasetKey;
  }
  
  /**
   * Release the catalogue into a new dataset
   * @param catalogueKey the draft catalogue to be released, e.g. 3 for the CoL draft
   */
  public static CatalogueRelease release(SqlSessionFactory factory, int catalogueKey) {
    Dataset release;
    try (SqlSession session = factory.openSession(true)) {
      release = new Dataset();
      release.setType(DatasetType.CATALOGUE);
      //TODO: create new release dataset
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.create(release);
    }
    return new CatalogueRelease(factory, catalogueKey, release.getKey());
  }
  
  public static CatalogueRelease reRelease(SqlSessionFactory factory, int catalogueKey, int datasetReleaseKey) {
    return new CatalogueRelease(factory, catalogueKey, datasetReleaseKey);
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
    // update metadata
    updateMetadata();
    return releaseDatasetKey;
  }
  
  private void updateMetadata() {
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Dataset d = dm.get(releaseDatasetKey);
      //TODO:
    }
  }
  private void mapIds() {
    //TODO:
  }
  
  private void copyData() {
  
  }
  
}
