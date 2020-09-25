package life.catalogue.dao;

import life.catalogue.api.model.DatasetScoped;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

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

  public static synchronized void partition(SqlSessionFactory factory, int datasetKey) {
    try (SqlSession session = factory.openSession(false)) {
      partition(session, datasetKey);
      session.commit();
    }
  }

  /**
   * Creates all dataset partitions needed, removing any previous partition and data for the given datasetKey.
   * To avoid table deadlocks we synchronize this method!
   * See https://github.com/Sp2000/colplus-backend/issues/127
   */
  public static synchronized void partition(SqlSession session, int datasetKey) {
    interruptIfCancelled();
    LOG.info("Create empty partition for dataset {}", datasetKey);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    // first remove if existing
    mapper.delete(datasetKey);

    // then create
    mapper.create(datasetKey);
  }

  /**
   * Creates dataset specific sequences for the global non partitioned tables like sector.
   */
  public static void createManagedSequences(SqlSessionFactory factory, int datasetKey) {
    interruptIfCancelled();
    LOG.info("Create sequences for managed dataset {}", datasetKey);
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DatasetPartitionMapper.class).createManagedSequences(datasetKey);
    }
  }

  /**
   * Creates dataset specific tables & objects that are needed for managed datasets only.
   * E.g. usage count triggers or id mapping tables.
   *
   * This requires the partitions to be attached already!
   */
  public static void createManagedObjects(SqlSessionFactory factory, int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      createManagedObjects(session, datasetKey);
    }
  }

  public static void createManagedObjects(SqlSession session, int datasetKey) {
    interruptIfCancelled();
    LOG.info("Create triggers for managed dataset {}", datasetKey);
    DatasetPartitionMapper dmp = session.getMapper(DatasetPartitionMapper.class);
    dmp.attachUsageCounter(datasetKey);
  }
  
  public static synchronized void delete(SqlSessionFactory factory, int datasetKey) {
    try (SqlSession session = factory.openSession(false)) {
      delete(session, datasetKey);
      session.commit();
    }
  }
  
  public static synchronized void delete(SqlSession session, int datasetKey) {
    interruptIfCancelled();
    LOG.info("Delete partition for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).delete(datasetKey);
  }

  public static synchronized void indexAndAttach(SqlSessionFactory factory, int datasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      // build indices and add dataset bound constraints
      indexAndAttach(session, datasetKey);
    }
  }

  /**
   * Builds indices and finally attaches partitions to main tables.
   * To avoid table deadlocks on the main table we synchronize this method.
   * @param session session with auto commit - no transaction allowed here !!!
   */
  public static synchronized void indexAndAttach(SqlSession session, int datasetKey) {
    interruptIfCancelled();

    LOG.info("Build partition indices for dataset {}", datasetKey);
    // build indices and add dataset bound constraints
    session.getMapper(DatasetPartitionMapper.class).buildIndices(datasetKey);

    // attach to main table - this requires an AccessExclusiveLock on all main tables
    // see https://github.com/Sp2000/colplus-backend/issues/387
    LOG.info("Attach partition tables for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).attach(datasetKey);
  }
}
