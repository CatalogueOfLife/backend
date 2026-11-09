package life.catalogue.event;

import life.catalogue.api.event.Event;
import life.catalogue.db.PgConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

/**
 * Broadcasts events with postgres LISTEN/NOTIFY to every app connected to the same database.
 *
 * Publishing sends pg_notify on a pooled connection. Postgres delivers a notification to every session listening on
 * the channel, the publishing app included, once the sending transaction commits. Each app listens on one dedicated
 * autocommit connection to the primary, never inside a transaction, as a listener in an open transaction receives
 * nothing and keeps the shared notification queue from being trimmed.
 *
 * The connection LISTENs from the moment the broker is built. Notifications wait on it until {@link #start()}
 * starts the thread that reads and dispatches them, so nothing published in between is lost.
 *
 * Notifications are not stored: while the connection is down they are gone. After every reconnect all listeners
 * flush their caches. Every {@link BrokerConfig#checkSeconds} the connection is probed with
 * pg_notification_queue_usage(), which also tells whether some listener on the database is stuck.
 *
 * On an app whose main database is a read only standby, events are handled only once the standby has replayed the
 * change: the WAL position of the primary is read right after the notification arrived, which lies past the commit
 * that sent it, and the standby is polled until it has replayed that far. Otherwise a listener reloading data from the
 * standby would cache the state before the change.
 */
public class PgEventBroker extends AbstractEventBroker {
  private static final Logger LOG = LoggerFactory.getLogger(PgEventBroker.class);
  private static final long POLL_MILLIS = 1000;
  private static final long STANDBY_POLL_MILLIS = 25;
  private static final double QUEUE_USAGE_WARNING = 0.5;

  private final BrokerConfig cfg;
  private final PgConfig listenDb;
  private final String applicationName;
  private final EventCodec codec = new EventCodec();
  private final @Nullable SqlSessionFactory publishFactory;
  private final @Nullable SqlSessionFactory standbyFactory;
  private volatile PgConnection conn;
  private volatile Thread thread;
  private volatile boolean running;
  private volatile boolean missedEvents;
  private volatile long lastCheck;
  private volatile double queueUsage;
  private volatile String lastError;

  /**
   * @param listenDb the primary database to LISTEN on
   * @param publishFactory pooled connections to the primary to publish with. If null, events are published on a
   *                       short lived connection to the listenDb
   * @param standbyFactory pooled connections to the read only standby this app reads from, if it does
   */
  public PgEventBroker(BrokerConfig cfg, PgConfig listenDb, @Nullable SqlSessionFactory publishFactory,
                       @Nullable SqlSessionFactory standbyFactory) {
    this.cfg = cfg;
    this.listenDb = listenDb;
    this.publishFactory = publishFactory;
    this.standbyFactory = standbyFactory;
    this.applicationName = (listenDb.applicationName == null ? "clb-" + cfg.name : listenDb.applicationName) + "-events";
    try {
      conn = connect();
    } catch (SQLException | RuntimeException e) {
      // the listening thread retries, flushing all caches once it is connected
      LOG.error("Event broker {} cannot listen on {} yet", cfg.name, listenDb.location(), e);
      lastError = e.toString();
      missedEvents = true;
    }
  }

  private PgConnection connect() throws SQLException {
    PgConnection c = listenDb.connectLongLived(applicationName);
    try {
      c.setAutoCommit(true);
      try (Statement st = c.createStatement()) {
        try (ResultSet rs = st.executeQuery("SELECT pg_is_in_recovery()")) {
          rs.next();
          if (rs.getBoolean(1)) {
            throw new IllegalStateException(listenDb.location() + " is a standby which cannot LISTEN, configure broker.db with the primary");
          }
        }
        // the channel is validated by BrokerConfig, an identifier cannot be bound
        st.execute("LISTEN " + cfg.channel);
      }
      lastCheck = System.currentTimeMillis();
      LOG.info("Event broker {} listens on channel {} of {}", cfg.name, cfg.channel, listenDb.location());
      return c;
    } catch (SQLException | RuntimeException e) {
      closeQuietly(c);
      throw e;
    }
  }

  @Override
  public void publish(Event event) {
    final String payload = codec.encode(event);
    LOG.debug("Publish event {}", payload);
    try {
      if (publishFactory != null) {
        try (SqlSession session = publishFactory.openSession(true)) {
          notify(session.getConnection(), payload);
        }
      } else {
        try (Connection c = listenDb.connect()) {
          notify(c, payload);
        }
      }
    } catch (SQLException | RuntimeException e) {
      // the change that caused the event is committed already, so do not fail the caller - but say so loudly
      LOG.error("Failed to publish event {}", payload, e);
    }
  }

  private void notify(Connection c, String payload) throws SQLException {
    try (PreparedStatement ps = c.prepareStatement("SELECT pg_notify(?, ?)")) {
      ps.setString(1, cfg.channel);
      ps.setString(2, payload);
      ps.execute();
    }
  }

  @Override
  public synchronized void start() {
    if (hasStarted()) return;
    running = true;
    thread = new Thread(this::listen, "event-listener-" + cfg.name);
    thread.setDaemon(true);
    thread.start();
    LOG.info("Started event broker {}", cfg.name);
  }

  @Override
  public synchronized void stop() {
    running = false;
    var t = thread;
    if (t != null) {
      t.interrupt();
      try {
        t.join(TimeUnit.SECONDS.toMillis(5));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      thread = null;
    }
    closeQuietly(conn);
    conn = null;
    LOG.info("Stopped event broker {}", cfg.name);
  }

  @Override
  public boolean hasStarted() {
    var t = thread;
    return t != null && t.isAlive();
  }

  private void listen() {
    long backoff = 1000;
    while (running) {
      try {
        if (conn == null) {
          conn = connect();
          backoff = 1000;
        }
        if (missedEvents) {
          missedEvents = false;
          flushAll("events may have been missed while the event broker was not connected");
        }
        PGNotification[] batch = conn.getNotifications((int) POLL_MILLIS);
        if (batch != null && batch.length > 0) {
          handle(batch);
        }
        if (System.currentTimeMillis() - lastCheck >= TimeUnit.SECONDS.toMillis(cfg.checkSeconds)) {
          check();
        }
      } catch (SQLException | RuntimeException e) {
        if (!running) break;
        LOG.error("Event broker {} lost its connection, reconnect in {}ms", cfg.name, backoff, e);
        lastError = e.toString();
        closeQuietly(conn);
        conn = null;
        missedEvents = true;
        try {
          Thread.sleep(backoff);
        } catch (InterruptedException ie) {
          break;
        }
        backoff = Math.min(backoff * 2, TimeUnit.SECONDS.toMillis(cfg.maxReconnectSeconds));
      }
    }
    LOG.info("Event broker {} stopped listening", cfg.name);
  }

  private void handle(PGNotification[] batch) throws SQLException {
    List<Event> events = new ArrayList<>(batch.length);
    for (PGNotification n : batch) {
      if (cfg.channel.equals(n.getName())) {
        Event e = codec.decode(n.getParameter());
        if (e != null) {
          events.add(e);
        }
      }
    }
    if (events.isEmpty()) return;
    if (standbyFactory != null) {
      awaitStandby(currentLsn(conn));
    }
    for (Event e : events) {
      dispatch(e);
    }
  }

  /**
   * @return the current WAL insert position of the primary. The insert and not the write position, as the primary
   *         may not flush every commit right away (synchronous_commit = off)
   */
  private static String currentLsn(Connection c) throws SQLException {
    try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT pg_current_wal_insert_lsn()::text")) {
      rs.next();
      return rs.getString(1);
    }
  }

  /**
   * Waits until the standby has replayed the primary's WAL up to the given position, or the configured time passed.
   * A database that is no standby has nothing to wait for.
   */
  @VisibleForTesting
  void awaitStandby(String lsn) {
    final long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cfg.standbyWaitSeconds);
    try (SqlSession session = standbyFactory.openSession(true);
         PreparedStatement ps = session.getConnection().prepareStatement("SELECT coalesce(pg_last_wal_replay_lsn() >= ?::pg_lsn, true)")
    ) {
      ps.setString(1, lsn);
      while (true) {
        try (ResultSet rs = ps.executeQuery()) {
          rs.next();
          if (rs.getBoolean(1)) return;
        }
        if (System.currentTimeMillis() > deadline) {
          LOG.warn("The standby did not replay up to {} within {}s, handle the events anyway", lsn, cfg.standbyWaitSeconds);
          return;
        }
        Thread.sleep(STANDBY_POLL_MILLIS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (SQLException | RuntimeException e) {
      LOG.warn("Failed to wait for the standby to replay up to {}, handle the events anyway", lsn, e);
    }
  }

  private void check() throws SQLException {
    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT pg_notification_queue_usage()")) {
      rs.next();
      queueUsage = rs.getDouble(1);
    }
    lastCheck = System.currentTimeMillis();
    if (queueUsage >= QUEUE_USAGE_WARNING) {
      LOG.warn("The postgres notification queue is {}% full, a listener on the database is stuck", Math.round(queueUsage * 100));
    }
  }

  @Override
  public boolean isHealthy() {
    return hasStarted()
      && conn != null
      && System.currentTimeMillis() - lastCheck < 3 * TimeUnit.SECONDS.toMillis(cfg.checkSeconds)
      && queueUsage < QUEUE_USAGE_WARNING;
  }

  @Override
  public String status() {
    return String.format("Event broker %s %s on channel %s of %s, last checked %ss ago, notification queue %.1f%% full%s",
      cfg.name,
      !hasStarted() ? "is not started" : conn == null ? "is not connected" : "listens",
      cfg.channel, listenDb.location(),
      (System.currentTimeMillis() - lastCheck) / 1000,
      queueUsage * 100,
      lastError == null ? "" : ". Last error: " + lastError
    );
  }

  @VisibleForTesting
  int backendPid() throws SQLException {
    return conn.getBackendPID();
  }

  private static void closeQuietly(@Nullable Connection c) {
    if (c != null) {
      try {
        c.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }
}
