package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoUtils {

  private static final Logger LOG = LoggerFactory.getLogger(DaoUtils.class);

  public static Dataset requireManaged(int datasetKey, SqlSession session) {
    Dataset d = session.getMapper(DatasetMapper.class).get(datasetKey);
    if (d.getDeleted() != null) {
      throw new IllegalArgumentException("The dataset " + datasetKey + " is deleted and cannot be modified.");
    }
    if (d.getOrigin() != DatasetOrigin.MANAGED) {
      throw new IllegalArgumentException("Only data from managed datasets can be modified. Dataset " + datasetKey + " is of origin " + d.getOrigin());
    }
    return d;
  }

  public static Dataset requireManaged(int datasetKey, SqlSessionFactory factory) {
    try (SqlSession s = factory.openSession()) {
      return requireManaged(datasetKey, s);
    }
  }

  public static void aquireTableLock(int datasetKey, SqlSession session) {
    LOG.info("Try to aquire a table lock for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).lockTables(datasetKey);
  }

  /**
   * Makes sure a given dataset key belongs to a dataset that can be modified,
   * i.e. it exists, it is not deleted, released or the names index.
   * @param datasetKey
   * @param action for "cannot be xxx" for logging messages only
   * @throws IllegalArgumentException if the dataset key should not be modified
   */
  public static Dataset assertMutable(int datasetKey, String action, SqlSession session) throws IllegalArgumentException {
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    Dataset d = dm.get(datasetKey);
    if (d == null) {
      throw NotFoundException.notFound(Dataset.class, datasetKey);
    } else if (d.hasDeletedDate()) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is deleted and cannot be " + action);
    } else if (d.getOrigin() == DatasetOrigin.RELEASED) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is released and cannot be " + action);
    }
    return d;
  }
}
