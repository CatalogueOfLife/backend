package life.catalogue.event;

import life.catalogue.api.event.*;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class PgEventBrokerTest {
  private static final AtomicInteger CHANNEL = new AtomicInteger();

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  static class Recorder implements DatasetListener, UserListener {
    final List<Event> events = new CopyOnWriteArrayList<>();
    final AtomicInteger flushes = new AtomicInteger();

    @Override
    public void datasetChanged(DatasetChanged e) {
      events.add(e);
    }

    @Override
    public void datasetDataChanged(DatasetDataChanged e) {
      events.add(e);
    }

    @Override
    public void userChanged(UserChanged e) {
      events.add(e);
    }

    @Override
    public void userPermissionChanged(UserPermissionChanged e) {
      events.add(e);
    }

    @Override
    public void flushCaches() {
      flushes.incrementAndGet();
    }
  }

  /**
   * A config with a channel of its own, so the brokers of one test never hear another test's events.
   */
  static BrokerConfig cfg() {
    var cfg = new BrokerConfig();
    cfg.channel = "test_events_" + CHANNEL.incrementAndGet();
    cfg.maxReconnectSeconds = 1;
    return cfg;
  }

  static PgEventBroker broker(BrokerConfig cfg) {
    return new PgEventBroker(cfg, SqlSessionFactoryRule.getCfg(), SqlSessionFactoryRule.getSqlSessionFactory(), null);
  }

  static void await(BooleanSupplier condition) throws InterruptedException {
    for (int i = 0; i < 400 && !condition.getAsBoolean(); i++) {
      TimeUnit.MILLISECONDS.sleep(25);
    }
    assertTrue(condition.getAsBoolean());
  }

  static void notifyRaw(String channel, String payload) throws Exception {
    try (Connection c = SqlSessionFactoryRule.getCfg().connect();
         PreparedStatement ps = c.prepareStatement("SELECT pg_notify(?, ?)")
    ) {
      ps.setString(1, channel);
      ps.setString(2, payload);
      ps.execute();
    }
  }

  @Test
  public void everyBrokerReceivesEverything() throws Exception {
    var cfg = cfg();
    var ra = new Recorder();
    var rb = new Recorder();
    try (var a = broker(cfg); var b = broker(cfg)) {
      a.register(ra);
      b.register(rb);
      a.start();
      b.start();
      assertTrue(a.hasStarted());

      var e = new DatasetDataChanged(1000, 7);
      a.publish(e);
      b.publish(new UserPermissionChanged("Peter"));
      await(() -> ra.events.size() == 2 && rb.events.size() == 2);
      assertEquals(List.of(e, new UserPermissionChanged("Peter")), ra.events);
      assertEquals(ra.events, rb.events);
      assertEquals(0, ra.flushes.get());
      assertTrue(a.isHealthy());
      assertTrue(a.status().contains("listens"));
      assertEquals(2, a.recent().size());
    }
  }

  /**
   * A listener throwing an Error neither keeps an event from the other listeners nor stops the broker listening.
   */
  @Test
  public void listenerErrorsKeepListening() throws Exception {
    var cfg = cfg();
    var failing = new Recorder() {
      @Override
      public void datasetDataChanged(DatasetDataChanged e) {
        super.datasetDataChanged(e);
        throw new StackOverflowError("listener too deep");
      }

      @Override
      public void flushCaches() {
        super.flushCaches();
        throw new StackOverflowError("flush too deep");
      }
    };
    var r = new Recorder();
    try (var a = broker(cfg)) {
      a.register(failing);
      a.register(r);
      a.start();
      a.publish(new DatasetDataChanged(1, 1));
      a.publish(new FlushCaches());
      a.publish(new DatasetDataChanged(2, 2));
      await(() -> r.events.size() == 2);
      assertEquals(1, r.flushes.get());
      assertEquals(2, failing.events.size());
      assertEquals(1, failing.flushes.get());
      assertTrue(a.hasStarted());
    }
  }

  /**
   * Reads on the listening connection time out, so a connection that died without a word is noticed by the next
   * check instead of blocking the listener until TCP gives up. Waiting for notifications must not lift the timeout.
   */
  @Test
  public void listenConnectionTimesOut() throws Exception {
    try (var c = SqlSessionFactoryRule.getCfg().connectLongLived("test-events", 7)) {
      assertEquals(7000, c.getNetworkTimeout());
      c.getNotifications(50);
      assertEquals(7000, c.getNetworkTimeout());
    }
  }

  /**
   * The broker listens from the moment it is built, so an event sent before it is started is still delivered.
   */
  @Test
  public void eventsBeforeStartAreDelivered() throws Exception {
    var cfg = cfg();
    var r = new Recorder();
    try (var a = broker(cfg); var b = broker(cfg)) {
      a.register(r);
      b.publish(new DatasetDataChanged(1, 1));
      TimeUnit.MILLISECONDS.sleep(100);
      assertTrue(r.events.isEmpty());
      a.start();
      await(() -> r.events.size() == 1);
    }
  }

  @Test
  public void unreadableEventsAreSkipped() throws Exception {
    var cfg = cfg();
    var r = new Recorder();
    try (var a = broker(cfg)) {
      a.register(r);
      a.start();
      notifyRaw(cfg.channel, "{\"t\":\"martian\",\"key\":1}");
      notifyRaw(cfg.channel, "no json at all");
      // unknown properties of a known type are ignored
      notifyRaw(cfg.channel, "{\"t\":\"perm\",\"username\":\"Ida\",\"since\":\"2030\"}");
      await(() -> r.events.size() == 1);
      assertEquals(new UserPermissionChanged("Ida"), r.events.get(0));
      assertTrue(a.hasStarted());
    }
  }

  @Test
  public void oversizeEventFlushesCaches() throws Exception {
    var cfg = cfg();
    var r = new Recorder();
    try (var a = broker(cfg)) {
      a.register(r);
      a.start();
      var e = new DatasetChanged();
      e.type = EventType.DELETE;
      e.key = 13;
      for (int i = 0; i < 1000; i++) {
        e.usernamesToInvalidate.add("user" + i);
      }
      a.publish(e);
      await(() -> r.flushes.get() == 1);
      assertTrue(r.events.isEmpty());
    }
  }

  /**
   * Notifications sent while the connection is down are lost, so every reconnect flushes all caches.
   */
  @Test
  public void reconnectFlushesCaches() throws Exception {
    var cfg = cfg();
    var r = new Recorder();
    try (var a = broker(cfg)) {
      a.register(r);
      a.start();
      final int pid = a.backendPid();
      try (Connection c = SqlSessionFactoryRule.getCfg().connect();
           PreparedStatement ps = c.prepareStatement("SELECT pg_terminate_backend(?)")
      ) {
        ps.setInt(1, pid);
        ps.execute();
      }
      await(() -> r.flushes.get() == 1);
      await(a::isHealthy);
      assertNotEquals(pid, a.backendPid());

      a.publish(new DatasetDataChanged(2, 2));
      await(() -> r.events.size() == 1);
      assertEquals(1, r.flushes.get());
    }
  }

  /**
   * A broker of an app reading from a standby waits for the standby to replay a change before dispatching.
   * The test database is no standby, so there is nothing to wait for, but the whole path runs.
   */
  @Test
  public void standbyWait() throws Exception {
    var cfg = cfg();
    cfg.standbyWaitSeconds = 5;
    var r = new Recorder();
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    try (var a = new PgEventBroker(cfg, SqlSessionFactoryRule.getCfg(), null, factory)) {
      a.register(r);
      a.start();
      // published on a short lived connection to the primary, as there is no publish factory
      a.publish(new DatasetDataChanged(3, 3));
      await(() -> r.events.size() == 1);
      // a primary has nothing to replay, so even an lsn far ahead returns at once
      cfg.standbyWaitSeconds = 0;
      long start = System.currentTimeMillis();
      a.awaitStandby("FFFFFFFF/FFFFFFFF");
      assertTrue(System.currentTimeMillis() - start < 2000);
    }
  }

  @Test
  public void buildsByMode() throws Exception {
    var cfg = cfg();
    cfg.mode = BrokerConfig.Mode.LOCAL;
    try (var b = EventBrokers.build(cfg, SqlSessionFactoryRule.getCfg(), SqlSessionFactoryRule.getSqlSessionFactory())) {
      assertTrue(b instanceof LocalEventBroker);
    }
    cfg.mode = BrokerConfig.Mode.NOTIFY;
    try (var b = EventBrokers.build(cfg, SqlSessionFactoryRule.getCfg(), SqlSessionFactoryRule.getSqlSessionFactory())) {
      assertTrue(b instanceof PgEventBroker);
    }
  }
}
