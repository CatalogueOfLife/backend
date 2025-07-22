package life.catalogue.dw.logging.pg;

import life.catalogue.api.model.ApiLog;
import life.catalogue.db.mapper.ApiLogsMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PgLogCollector implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(PgLogCollector.class);
  private final int maxSize;
  private final long maxTime;
  private long lastPersist;
  private SqlSessionFactory factory;
  private List<ApiLog> logs;


  public PgLogCollector(PgLogConfig cfg) {
    this.maxSize = cfg.maxSize;
    this.maxTime = (long) cfg.maxTime * 1000; // we use milliseconds internally
    lastPersist = System.currentTimeMillis();
    clearLogs();
  }

  private void clearLogs() {
    logs = new ArrayList<>(maxSize +10);
  }

  public void setFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public void add(ApiLog log) {
    logs.add(log);
    if (logs.size() >= maxSize || System.currentTimeMillis() - lastPersist > maxTime) {
      // persist asynchroneously to not slow down current response
      persistAsync(logs);
      clearLogs();
    }
  }

  private void persistAsync(List<ApiLog> logs) {
    CompletableFuture.runAsync(() -> {
      persist(logs);
    });
  }

  private void persist(List<ApiLog> logs) {
    LOG.debug("Persisting {} logs to DB", logs.size());
    lastPersist = System.currentTimeMillis();
    try (SqlSession session = factory.openSession(false)) {
      var mapper = session.getMapper(ApiLogsMapper.class);
      for (ApiLog log : logs) {
        mapper.create(log);
      }
      session.commit();
    } catch (Exception e) {
      LOG.error("Persisting logs failed", e);
    }
  }

  @Override
  public void close() throws Exception {
    LOG.info("Shutting down log collector with {} logs left", logs.size());
    persist(logs);
  }
}
