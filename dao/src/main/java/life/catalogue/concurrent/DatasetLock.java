package life.catalogue.concurrent;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * A dataset wide lock that keeps processes such as an import, an export or an extended release that merges a dataset
 * from working on the same dataset in conflicting ways.
 *
 * A dataset is either locked EXCLUSIVE by a single job that changes its data, e.g. an import, or SHARED by any number
 * of jobs that only read it, e.g. exports or the merge of an extended release. Locks are held by job keys and are
 * re-entrant. Nothing ever waits here: tryLock() answers at once, and whoever cares to wait registers a listener
 * that is told whenever a dataset got unlocked - the JobExecutor parks blocked jobs that way.
 */
public class DatasetLock {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetLock.class);

  public enum Mode {
    /**
     * Readers. Any number of them may hold the dataset at once.
     */
    SHARED,
    /**
     * A single writer.
     */
    EXCLUSIVE
  }

  private static class Holders {
    Mode mode;
    final Set<UUID> jobs = new LinkedHashSet<>();

    Holders(Mode mode) {
      this.mode = mode;
    }
  }

  private static final Map<Integer, Holders> LOCKS = new HashMap<>();
  private static final List<IntConsumer> LISTENERS = new CopyOnWriteArrayList<>();

  private DatasetLock() {
  }

  /**
   * Tries to lock a dataset for a job without waiting.
   * A job that holds the lock already gets it again, a sole SHARED holder can also upgrade to EXCLUSIVE.
   *
   * @return null if the job holds the lock now, otherwise the key of a job holding a conflicting lock
   */
  public static synchronized @Nullable UUID tryLock(int datasetKey, UUID job, Mode mode) {
    Holders h = LOCKS.get(datasetKey);
    if (h == null) {
      h = new Holders(mode);
      h.jobs.add(job);
      LOCKS.put(datasetKey, h);
      return null;
    }
    if (h.jobs.contains(job)) {
      if (mode == Mode.EXCLUSIVE && h.mode == Mode.SHARED) {
        if (h.jobs.size() > 1) {
          return other(h, job);
        }
        h.mode = Mode.EXCLUSIVE;
      }
      return null;
    }
    if (mode == Mode.SHARED && h.mode == Mode.SHARED) {
      h.jobs.add(job);
      return null;
    }
    return other(h, job);
  }

  private static UUID other(Holders h, UUID job) {
    return h.jobs.stream().filter(j -> !j.equals(job)).findFirst().orElseThrow();
  }

  /**
   * Releases the lock a job holds on a dataset. Nothing happens if the job holds none,
   * so a job that never got its lock cannot release the one of another job.
   *
   * @return true if the job held a lock on the dataset
   */
  public static boolean unlock(int datasetKey, UUID job) {
    boolean released;
    synchronized (DatasetLock.class) {
      released = remove(datasetKey, job);
    }
    if (released) {
      notifyListeners(datasetKey);
    }
    return released;
  }

  /**
   * Releases all locks a job holds, e.g. when it ended.
   *
   * @return the keys of the datasets the job held a lock on
   */
  public static Set<Integer> unlockAll(UUID job) {
    Set<Integer> released = new HashSet<>();
    synchronized (DatasetLock.class) {
      for (var datasetKey : List.copyOf(LOCKS.keySet())) {
        if (remove(datasetKey, job)) {
          released.add(datasetKey);
        }
      }
    }
    released.forEach(DatasetLock::notifyListeners);
    return released;
  }

  private static boolean remove(int datasetKey, UUID job) {
    Holders h = LOCKS.get(datasetKey);
    if (h != null && h.jobs.remove(job)) {
      if (h.jobs.isEmpty()) {
        LOCKS.remove(datasetKey);
      }
      return true;
    }
    return false;
  }

  /**
   * Listeners are called outside of the lock, on the thread that released it.
   */
  private static void notifyListeners(int datasetKey) {
    for (var l : LISTENERS) {
      try {
        l.accept(datasetKey);
      } catch (RuntimeException e) {
        LOG.error("Dataset unlock listener failed for dataset {}", datasetKey, e);
      }
    }
  }

  /**
   * @return the mode the dataset is currently locked in, empty if it is not locked
   */
  public static synchronized Optional<Mode> isLocked(int datasetKey) {
    Holders h = LOCKS.get(datasetKey);
    return h == null ? Optional.empty() : Optional.of(h.mode);
  }

  /**
   * @return true if the given job could lock the dataset in the given mode right now
   */
  public static synchronized boolean isAvailable(int datasetKey, UUID job, Mode mode) {
    Holders h = LOCKS.get(datasetKey);
    if (h == null || h.jobs.contains(job) && (mode == Mode.SHARED || h.jobs.size() == 1)) {
      return true;
    }
    return mode == Mode.SHARED && h.mode == Mode.SHARED;
  }

  /**
   * @return the keys of the jobs holding a lock on the dataset
   */
  public static synchronized Set<UUID> holders(int datasetKey) {
    Holders h = LOCKS.get(datasetKey);
    return h == null ? Set.of() : Set.copyOf(h.jobs);
  }

  /**
   * Registers a listener that is given the key of every dataset a job released its lock on.
   */
  public static void addListener(IntConsumer listener) {
    LISTENERS.add(listener);
  }

  public static void removeListener(IntConsumer listener) {
    LISTENERS.remove(listener);
  }

  @VisibleForTesting
  static synchronized void clear() {
    LOCKS.clear();
  }
}
