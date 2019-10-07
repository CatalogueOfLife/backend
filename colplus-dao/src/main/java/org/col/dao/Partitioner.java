package org.col.dao;

import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.DatasetScoped;
import org.col.db.mapper.DatasetPartitionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.common.lang.Exceptions.interruptIfCancelled;

public class Partitioner {
  private static final Logger LOG = LoggerFactory.getLogger(Partitioner.class);
  
  public static String partition(int datasetKey) {
    return datasetKey > 100000 ? "plazi" : String.valueOf(datasetKey);
  }
  
  public static String partition(DatasetScoped key) {
    return partition(key.getDatasetKey());
  }
  
  /**
   * partition by DatasetScoped like method that takes a hashmap which can be included as column parameters
   * in mybatis xml collection statements, e.g. NameUsageWrapperMapper.taxonGetClassificationResultMap
   */
  public static String partition(Map<String, Object> key) {
    return partition( (Integer) key.get("datasetKey"));
  }

  /**
   * Creates all dataset partitions needed, removing any previous partition and data for the given datasetKey.
   * To avoid table deadlocks we synchronize this method!
   * See https://github.com/Sp2000/colplus-backend/issues/127
   */
  public static synchronized void partition(SqlSessionFactory factory, int datasetKey) {
    interruptIfCancelled();
    LOG.info("Create empty partition for dataset {}", datasetKey);
    try (SqlSession session = factory.openSession(false)) {
      DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
      // first remove if existing
      mapper.delete(datasetKey);
      
      // then create
      mapper.create(datasetKey);
      session.commit();
    }
  }
  
  /**
   * Builds indices and finally attaches partitions to main tables.
   * To avoid table deadlocks on the main table we synchronize this method.
   */
  public static synchronized void indexAndAttach(SqlSessionFactory factory, int datasetKey) {
    interruptIfCancelled();
    LOG.info("Build partition indices for dataset {}", datasetKey);
    try (SqlSession session = factory.openSession(true)) {
      // build indices and add dataset bound constraints
      session.getMapper(DatasetPartitionMapper.class).buildIndices(datasetKey);
    }
    
    try (SqlSession session = factory.openSession(true)) {
      // attach to main table - this requires an AccessExclusiveLock on all main tables
      // see https://github.com/Sp2000/colplus-backend/issues/387
      session.getMapper(DatasetPartitionMapper.class).attach(datasetKey);
    }
  }
}
