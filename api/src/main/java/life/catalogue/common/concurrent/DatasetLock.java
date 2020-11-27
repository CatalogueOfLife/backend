package life.catalogue.common.concurrent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A dataset wide lock that is used to make sure different processes such as the
 * dataset importer or sector syncs don't work on the same dataset simultaneously.
 */
public class DatasetLock {
  private static final ConcurrentMap<Integer, UUID> LOCKS = new ConcurrentHashMap<>();

  public static synchronized boolean lock(int datasetKey, UUID process) {
    if (LOCKS.containsKey(datasetKey)) {
      return false;
    }
    LOCKS.put(datasetKey, process);
    return true;
  }

  public static UUID unlock(int datasetKey) {
    return LOCKS.remove(datasetKey);
  }

  public static Optional<UUID> isLocked(int datasetKey) {
    return Optional.ofNullable(LOCKS.get(datasetKey));
  }
}
