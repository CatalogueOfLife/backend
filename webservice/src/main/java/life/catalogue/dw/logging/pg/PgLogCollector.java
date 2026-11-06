package life.catalogue.dw.logging.pg;

import life.catalogue.api.model.ApiLog;
import life.catalogue.db.mapper.ApiLogsMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Collects api logs in memory and persists them in batches.
 * add() is called by every request thread at once, so the buffer is only ever touched under a lock:
 * a full buffer is swapped for an empty one under that lock and persisted by a single thread afterwards.
 * It used to be a plain ArrayList shared by all request threads, which lost logs, could fail a response filter
 * and, as several threads could find the same full list, persisted some batches twice.
 */
public class PgLogCollector implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(PgLogCollector.class);
  private final int maxSize;
  private final long maxTime;
  private final Object lock = new Object();
  private volatile SqlSessionFactory factory;
  // guarded by lock
  private List<ApiLog> logs;
  // guarded by lock
  private long lastPersist;


  public PgLogCollector(PgLogConfig cfg) {
    this.maxSize = cfg.maxSize;
    this.maxTime = (long) cfg.maxTime * 1000; // we use milliseconds internally
    lastPersist = System.currentTimeMillis();
    logs = newBuffer();
  }

  private List<ApiLog> newBuffer() {
    return new ArrayList<>(maxSize + 10);
  }

  public void setFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public void add(ApiLog log) {
    List<ApiLog> full = null;
    synchronized (lock) {
      logs.add(log);
      final long now = System.currentTimeMillis();
      // a maxTime of 0 means only the size matters
      if (logs.size() >= maxSize || (maxTime > 0 && now - lastPersist > maxTime)) {
        full = logs;
        logs = newBuffer();
        lastPersist = now;
      }
    }
    if (full != null) {
      // persist asynchroneously to not slow down current response
      persistAsync(full);
    }
  }

  /**
   * @return the logs collected so far, leaving an empty buffer
   */
  private List<ApiLog> drain() {
    synchronized (lock) {
      var all = logs;
      logs = newBuffer();
      lastPersist = System.currentTimeMillis();
      return all;
    }
  }

  @VisibleForTesting
  int size() {
    synchronized (lock) {
      return logs.size();
    }
  }

  @VisibleForTesting
  void persistAsync(List<ApiLog> logs) {
    CompletableFuture.runAsync(() -> persist(logs));
  }

  @VisibleForTesting
  void persist(List<ApiLog> logs) {
    if (logs.isEmpty()) {
      return;
    }
    LOG.debug("Persisting {} logs to DB", logs.size());
    try (SqlSession session = factory.openSession(ExecutorType.BATCH, false)) {
      var mapper = session.getMapper(ApiLogsMapper.class);
      for (ApiLog log : logs) {
        mapper.create(log);
      }
      session.commit();
    } catch (Exception e) {
      LOG.error("Persisting {} logs failed", logs.size(), e);
    }
  }

  @Override
  public void close() throws Exception {
    var rest = drain();
    LOG.info("Shutting down log collector with {} logs left", rest.size());
    persist(rest);
  }
}
