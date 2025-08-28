package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;

import java.util.function.IntPredicate;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class DaoUtils {

  private static final Logger LOG = LoggerFactory.getLogger(DaoUtils.class);


  public static void requireProject(int datasetKey) throws NotFoundException {
    requireProject(datasetKey, "Only data from projects can be modified.");
  }

  /**
   * @param datasetKey dataset to test
   * @param message end with a full stop
   * @throws NotFoundException if deleted or not existing
   * @throws IllegalArgumentException if not managed
   */
  public static void requireProject(int datasetKey, String message) throws NotFoundException {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (origin != DatasetOrigin.PROJECT) {
      throw new IllegalArgumentException(message + " Dataset " + datasetKey + " is of origin " + origin);
    }
  }

  /**
   * Makes sure the datasets origin is a project or a release type
   */
  public static void requireProjectOrRelease(int datasetKey) throws NotFoundException {
    requireProjectOrRelease(datasetKey, "A project or release is required.");
  }

  /**
   * @param datasetKey dataset to test
   * @param message end with a full stop
   * @throws NotFoundException if deleted or not existing
   * @throws IllegalArgumentException if not managed or released
   */
  public static void requireProjectOrRelease(int datasetKey, String message) throws NotFoundException {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (origin == null || !origin.isProjectOrRelease()) {
      throw new IllegalArgumentException(message + " Dataset " + datasetKey + " is of origin " + origin);
    }
  }

  /**
   * @param datasetKey dataset to test
   * @param origin to be required for the dataset key
   * @param message end with a full stop
   * @throws NotFoundException if deleted or not existing
   * @throws IllegalArgumentException if not of required origin
   */
  public static void requireOrigin(int datasetKey, DatasetOrigin origin, String message) throws NotFoundException {
    var info = DatasetInfoCache.CACHE.info(datasetKey);
    if (info == null || info.origin != origin) {
      throw new IllegalArgumentException(message + " Dataset " + datasetKey + " is of origin " + origin);
    }
  }

  /**
   * @param datasetKey dataset to test
   * @throws NotFoundException if deleted or not existing
   * @throws IllegalArgumentException if the dataset is a project
   */
  public static void notProject(int datasetKey) throws NotFoundException {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (origin == null || origin == DatasetOrigin.PROJECT) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is of origin " + origin);
    }
  }

  /**
   * Makes sure a given dataset key belongs to a dataset that can be modified,
   * i.e. it exists, it is not deleted or released.
   * @param datasetKey
   * @param action for "cannot be xxx" for logging messages only
   * @throws IllegalArgumentException if the dataset key points to a release
   * @throws NotFoundException if the dataset does not exist or was deleted
   */
  public static void notReleased(int datasetKey, String action) throws IllegalArgumentException {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (origin == null || origin.isRelease()) {
      throw new IllegalArgumentException("Dataset " + datasetKey + " is released and cannot be " + action);
    }
  }

  /**
   * List keys of all datasets that have data partitions.
   */
  public static IntSet listDatasetWithNames(SqlSession session) {
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    NameMapper nm = session.getMapper(NameMapper.class);
    IntSet keys = new IntOpenHashSet(dm.keys(false));
    // only keep the dataset with at least one name
    keys.removeIf((IntPredicate) key -> !nm.hasData(key));
    return keys;
  }

}
