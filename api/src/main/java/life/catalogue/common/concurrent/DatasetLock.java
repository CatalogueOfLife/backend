package life.catalogue.common.concurrent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A dataset wide lock that is used to make sure different processes such as the
 * dataset importer or sector syncs don't work on the same dataset simultaneously.
 */
public class DatasetLock {
  public enum ProcessType {
    IMPORT, SYNC, INDEXING, METRICS, OTHER
  }
  private static final ConcurrentMap<Integer, ProcessType> LOCKS = new ConcurrentHashMap<>();

  public static synchronized boolean lock(int datasetKey, ProcessType process) {
    if (LOCKS.containsKey(datasetKey)) {
      return false;
    }
    LOCKS.put(datasetKey, process);
    return true;
  }

  public static ProcessType unlock(int datasetKey) {
    return LOCKS.remove(datasetKey);
  }

  public static Optional<ProcessType> isLocked(int datasetKey) {
    return Optional.ofNullable(LOCKS.get(datasetKey));
  }
}
