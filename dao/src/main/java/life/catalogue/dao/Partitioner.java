package life.catalogue.dao;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DELETE;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 * Utils class that wraps the main DatasetPartitionMapper.
 * See DatasetPartitionMapper for the main documentation.
 */
public class Partitioner {
  private static final Logger LOG = LoggerFactory.getLogger(Partitioner.class);

  public static synchronized void createDefaultPartitions(SqlSessionFactory factory, int number) {
    try (SqlSession session = factory.openSession(false)) {
      createDefaultPartitions(session, number);
      session.commit();
    }
  }

  /**
   * Creates a given number of partitions for the default, hashed partition
   * including all required sequences.
   * @param number of partitions to create
   */
  public static synchronized void createDefaultPartitions(SqlSession session, int number) {
    LOG.info("Create default partition with {} subpartitions for external datasets", number);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    mapper.createDefaultPartitions(number);
    session.commit();
  }

  public static synchronized void partition(SqlSessionFactory factory, int datasetKey, DatasetOrigin origin) {
    try (SqlSession session = factory.openSession(false)) {
      partition(session, datasetKey, origin);
      session.commit();
    }
  }

  /**
   * Creates all dataset partitions needed, removing any previous partition and data for the given datasetKey.
   * To avoid table deadlocks we synchronize this method!
   * See https://github.com/Sp2000/colplus-backend/issues/127
   */
  public static synchronized void partition(SqlSession session, int datasetKey, DatasetOrigin origin) {
    interruptIfCancelled();
    LOG.info("Create empty partition for dataset {}", datasetKey);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    // first remove if existing
    mapper.delete(datasetKey, origin);
    // then create
    mapper.create(datasetKey, origin);
  }
  
  public static synchronized void delete(SqlSessionFactory factory, int datasetKey, DatasetOrigin origin) {
    try (SqlSession session = factory.openSession(false)) {
      delete(session, datasetKey, origin);
      session.commit();
    }
  }
  
  public static synchronized void delete(SqlSession session, int datasetKey, DatasetOrigin origin) {
    interruptIfCancelled();
    LOG.info("Delete partition for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).delete(datasetKey, origin);
  }

  public static synchronized void attach(SqlSessionFactory factory, int datasetKey, DatasetOrigin origin) {
    try (SqlSession session = factory.openSession(true)) {
      // build indices and add dataset bound constraints
      attach(session, datasetKey, origin);
    }
  }

  /**
   * Attaches partitions to main tables thereby building declared indices.
   * To avoid table deadlocks on the main table we synchronize this method.
   * @param session session with auto commit - no transaction allowed here !!!
   */
  public static synchronized void attach(SqlSession session, int datasetKey, DatasetOrigin origin) {
    interruptIfCancelled();
    // attach to main table - this requires an AccessExclusiveLock on all main tables
    // see https://github.com/Sp2000/colplus-backend/issues/387
    LOG.info("Attach partition tables for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).attach(datasetKey, origin);
  }
}
