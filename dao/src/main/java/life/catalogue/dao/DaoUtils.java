package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoUtils {

  private static final Logger LOG = LoggerFactory.getLogger(DaoUtils.class);

  public static void requireManaged(int datasetKey) throws NotFoundException {
    requireManaged(datasetKey, "Only data from managed datasets can be modified.");
  }

  /**
   * @param datasetKey dataset to test
   * @param message end with a full stop
   * @throws NotFoundException if deleted or not existing
   * @throws IllegalArgumentException if not managed
   */
  public static void requireManaged(int datasetKey, String message) throws NotFoundException {
    DatasetOrigin origin = DatasetInfoCache.CACHE.origin(datasetKey);
    if (origin != DatasetOrigin.MANAGED) {
      throw new IllegalArgumentException(message + " Dataset " + datasetKey + " is of origin " + origin);
    }
  }

  /**
   * Makes sure the datasets origin is a project and is either managed or released
   */
  public static void requireProject(int datasetKey) throws NotFoundException {
    requireProject(datasetKey, "Only data from managed datasets can be modified.");
  }

  /**
   * @param datasetKey dataset to test
   * @param message end with a full stop
   * @throws NotFoundException if deleted or not existing
   * @throws IllegalArgumentException if not managed or released
   */
  public static void requireProject(int datasetKey, String message) throws NotFoundException {
    DatasetOrigin origin = DatasetInfoCache.CACHE.origin(datasetKey);
    if (origin == null || !origin.isProject()) {
      throw new IllegalArgumentException(message + " Dataset " + datasetKey + " is of origin " + origin);
    }
  }

  public static void aquireTableLock(int datasetKey, SqlSession session) {
    LOG.info("Try to aquire a table lock for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).lockTables(datasetKey);
  }

  /**
   * Makes sure a given dataset key belongs to a dataset that can be modified,
   * i.e. it exists, it is not deleted or released.
   * @param datasetKey
   * @param action for "cannot be xxx" for logging messages only
   * @throws IllegalArgumentException if the dataset key should not be modified
   */
  public static Dataset assertMutable(int datasetKey, String action, SqlSession session) throws IllegalArgumentException {
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    Dataset d = dm.get(datasetKey);
    if (d == null || d.hasDeletedDate()) {
      throw NotFoundException.notFound(Dataset.class, datasetKey);
    } else if (d.getOrigin() == DatasetOrigin.RELEASED) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is released and cannot be " + action);
    }
    return d;
  }
}
