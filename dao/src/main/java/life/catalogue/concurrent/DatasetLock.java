package life.catalogue.concurrent;

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

  /**
   * Tries to acquire a lock for the requested dataset and returns the process key that currently has the lock.
   * If the a new lock could be acquired the process will the requested key.
   */
  public static synchronized UUID lock(int datasetKey, UUID process) {
    if (LOCKS.containsKey(datasetKey)) {
      return LOCKS.get(datasetKey);
    }
    LOCKS.put(datasetKey, process);
    return process;
  }

  public static UUID unlock(int datasetKey) {
    return LOCKS.remove(datasetKey);
  }

  public static Optional<UUID> isLocked(int datasetKey) {
    return Optional.ofNullable(LOCKS.get(datasetKey));
  }
}
