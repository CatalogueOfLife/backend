package life.catalogue.assembly;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import com.codahale.metrics.Timer;

/**
 * In memory counters for completed and failed sector syncs per project since the server started.
 * Updated by the sector jobs themselves when they finish and read by the SyncManager for its state reports.
 */
public class SyncCounter {
  private final Map<Integer, AtomicInteger> completed = new ConcurrentHashMap<>(); // by project datasetKey
  private final Map<Integer, AtomicInteger> failed = new ConcurrentHashMap<>();    // by project datasetKey
  private final @Nullable Timer timer;

  public SyncCounter(@Nullable Timer timer) {
    this.timer = timer;
  }

  public void completed(int projectKey, long durationSeconds) {
    completed.computeIfAbsent(projectKey, k -> new AtomicInteger()).incrementAndGet();
    if (timer != null) {
      timer.update(durationSeconds, TimeUnit.SECONDS);
    }
  }

  public void failed(int projectKey) {
    failed.computeIfAbsent(projectKey, k -> new AtomicInteger()).incrementAndGet();
  }

  public int getCompleted(int projectKey) {
    return valOrZero(completed, projectKey);
  }

  public int getFailed(int projectKey) {
    return valOrZero(failed, projectKey);
  }

  public int completedTotal() {
    return total(completed);
  }

  public int failedTotal() {
    return total(failed);
  }

  private static int total(Map<Integer, AtomicInteger> cnt) {
    return cnt.values().stream().mapToInt(AtomicInteger::get).sum();
  }

  private static int valOrZero(Map<Integer, AtomicInteger> map, int key) {
    var val = map.get(key);
    return val == null ? 0 : val.get();
  }
}
