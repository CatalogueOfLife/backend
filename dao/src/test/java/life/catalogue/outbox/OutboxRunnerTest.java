package life.catalogue.outbox;

import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.OutboxStatus;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.db.mapper.OutboxMapper;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.Assert.*;

public class OutboxRunnerTest extends MapperTestBase<OutboxMapper> {

  public OutboxRunnerTest() {
    super(OutboxMapper.class, TestDataRule.empty());
  }

  static <T> T om(Function<OutboxMapper, T> action) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return action.apply(session.getMapper(OutboxMapper.class));
    }
  }

  static OutboxConfig cfg() {
    var cfg = new OutboxConfig();
    cfg.pollMillis = 3_600_000; // tests poll themselves
    cfg.maxAttempts = 3;
    cfg.stopGraceSeconds = 1;
    return cfg;
  }

  static void enqueue(String key) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      Outbox.sectorDelete(session, Outbox.parseSectorKey(key), Users.TESTER);
    }
  }

  static OutboxEntry get(String key) {
    return om(m -> m.getByKey(OutboxType.SECTOR_DELETE, key));
  }

  /**
   * Claims the single due command like the poll thread does, but runs it on the calling thread.
   */
  static void claimAndRun(OutboxRunner runner, OutboxHandler handler) {
    var claimed = om(m -> m.claim(OutboxType.SECTOR_DELETE, 1, runner.getInstance()));
    assertEquals(1, claimed.size());
    runner.run(handler, claimed.get(0));
  }

  static class Handler implements OutboxHandler {
    final List<OutboxEntry> handled = new CopyOnWriteArrayList<>();
    final List<OutboxEntry> failed = new CopyOnWriteArrayList<>();
    volatile RuntimeException error;
    volatile boolean online = true;

    @Override
    public void handle(OutboxEntry entry) {
      handled.add(entry);
      if (error != null) throw error;
    }

    @Override
    public boolean isOnline() {
      return online;
    }

    @Override
    public Duration retryDelay(int attempts) {
      return Duration.ZERO;
    }

    @Override
    public void onFailed(OutboxEntry entry, Exception e) {
      failed.add(entry);
    }
  }

  @Test
  public void success() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler();
    enqueue("3:1");
    claimAndRun(runner, h);
    assertEquals(1, h.handled.size());
    assertEquals("3:1", h.handled.get(0).getKey());
    assertNull(get("3:1"));
  }

  @Test
  public void retryLaterDoesNotCount() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler();
    for (RuntimeException e : List.of(new RetryLaterException("busy"), new UnavailableException("executor stopped"))) {
      om(m -> m.resetStale());
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        session.getConnection().createStatement().execute("UPDATE outbox SET due = now()");
      }
      if (get("3:2") == null) enqueue("3:2");
      h.error = e;
      claimAndRun(runner, h);
      var x = get("3:2");
      assertEquals(OutboxStatus.PENDING, x.getStatus());
      assertEquals(0, x.getAttempts());
      assertNull(x.getClaimedBy());
      assertTrue(x.getLastError().contains(e.getMessage()));
    }
    assertTrue(h.failed.isEmpty());
  }

  @Test
  public void retryThenFailAfterMaxAttempts() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler();
    h.error = new IllegalStateException("flaky");
    enqueue("3:3");
    claimAndRun(runner, h);
    assertEquals(1, get("3:3").getAttempts());
    assertEquals(OutboxStatus.PENDING, get("3:3").getStatus());
    claimAndRun(runner, h);
    assertEquals(2, get("3:3").getAttempts());
    assertTrue(h.failed.isEmpty());
    // the third of 3 attempts fails it for good
    claimAndRun(runner, h);
    var x = get("3:3");
    assertEquals(OutboxStatus.FAILED, x.getStatus());
    assertEquals(3, x.getAttempts());
    assertEquals(1, h.failed.size());
  }

  @Test
  public void permanentFailsAtOnce() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler();
    h.error = new IllegalArgumentException("no such thing");
    enqueue("3:4");
    claimAndRun(runner, h);
    var x = get("3:4");
    assertEquals(OutboxStatus.FAILED, x.getStatus());
    assertEquals(1, x.getAttempts());
    assertEquals(1, h.failed.size());
  }

  /**
   * An Error is recorded as a failed attempt before it is rethrown, so the command does not stay running and claimed
   * by this app until it restarts.
   */
  @Test
  public void errorIsRecordedAndRethrown() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler() {
      @Override
      public void handle(OutboxEntry entry) {
        super.handle(entry);
        throw new StackOverflowError("too deep");
      }
    };
    enqueue("3:9");
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        claimAndRun(runner, h);
        fail("The error must be rethrown");
      } catch (StackOverflowError e) {
        // expected
      }
      var x = get("3:9");
      assertEquals(attempt, x.getAttempts());
      assertNull(x.getClaimedBy());
      assertTrue(x.getLastError().contains("too deep"));
      assertEquals(attempt < 3 ? OutboxStatus.PENDING : OutboxStatus.FAILED, x.getStatus());
    }
    assertEquals(1, h.failed.size());
  }

  @Test
  public void pollRunsOnlineHandlers() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var latch = new CountDownLatch(2);
    var h = new Handler() {
      @Override
      public void handle(OutboxEntry entry) {
        super.handle(entry);
        latch.countDown();
      }
    };
    runner.register(OutboxType.SECTOR_DELETE, h);
    enqueue("3:5");
    enqueue("3:6");
    runner.start();
    try {
      // offline handlers leave their commands waiting
      h.online = false;
      runner.poll();
      Thread.sleep(100);
      assertTrue(h.handled.isEmpty());
      assertEquals(2, (int) om(m -> m.count(OutboxType.SECTOR_DELETE, OutboxStatus.PENDING)));

      h.online = true;
      // concurrency 1 runs one at a time, so it takes several polls
      for (int i = 0; i < 200 && latch.getCount() > 0; i++) {
        runner.poll();
        Thread.sleep(50);
      }
      assertTrue(latch.await(1, TimeUnit.SECONDS));
      // the virtual thread completes the command right after handling it
      for (int i = 0; i < 100 && om(m -> m.count(null, null)) > 0; i++) {
        Thread.sleep(50);
      }
      assertEquals(0, (int) om(m -> m.count(null, null)));
    } finally {
      runner.stop();
    }
  }

  /**
   * A finished command makes room for the next due one right away, not only at the next poll, so a burst of DOI
   * updates drains as fast as DataCite answers.
   */
  @Test
  public void finishedCommandsMakeRoomAtOnce() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var latch = new CountDownLatch(5);
    var h = new Handler() {
      @Override
      public void handle(OutboxEntry entry) {
        super.handle(entry);
        latch.countDown();
      }
    };
    runner.register(OutboxType.SECTOR_DELETE, h);
    for (int i = 10; i < 15; i++) {
      enqueue("3:" + i);
    }
    runner.start();
    try {
      // a single poll, the next one is an hour away
      runner.poll();
      assertTrue("Only " + h.handled.size() + " of 5 commands ran", latch.await(10, TimeUnit.SECONDS));
    } finally {
      runner.stop();
    }
  }

  /**
   * The commands of a handler that is offline on purpose, like those of a stopped DoiUpdater, wait without being
   * reported as overdue.
   */
  @Test
  public void offlineHandlersAreNotOverdue() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler();
    runner.register(OutboxType.SECTOR_DELETE, h);
    enqueue("3:15");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getConnection().createStatement().execute("UPDATE outbox SET due = now() - interval '1 day'");
    }
    assertEquals(1, runner.health().overdue());
    h.online = false;
    var health = runner.health();
    assertEquals(0, health.overdue());
    assertEquals(1, health.pending());
  }

  /**
   * A request merged into a command while it ran gives it another try, so its failure is not reported as final.
   */
  @Test
  public void failedAfterMergeIsNotReported() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler() {
      @Override
      public void handle(OutboxEntry entry) {
        super.handle(entry);
        enqueue(entry.getKey());
        throw new IllegalArgumentException("rejected");
      }
    };
    enqueue("3:16");
    claimAndRun(runner, h);
    var x = get("3:16");
    assertEquals(OutboxStatus.PENDING, x.getStatus());
    assertTrue(h.failed.isEmpty());
  }

  /**
   * A command that keeps being put off, e.g. a sector deletion while the sector is always busy, is never overdue as
   * its due time moves on. It is reported once it was requested long ago, unless its handler is offline on purpose.
   */
  @Test
  public void stuckCommandsAreReported() throws Exception {
    var runner = new OutboxRunner(cfg(), SqlSessionFactoryRule.getSqlSessionFactory());
    var h = new Handler();
    runner.register(OutboxType.SECTOR_DELETE, h);
    enqueue("3:17");
    assertEquals(0, runner.health().stuck());
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getConnection().createStatement()
        .execute("UPDATE outbox SET created = now() - interval '3 days', due = now() + interval '1 minute'");
    }
    var health = runner.health();
    assertEquals(0, health.overdue());
    assertEquals(1, health.stuck());
    h.online = false;
    assertEquals(0, runner.health().stuck());
  }

  @Test
  public void startTakesOverAndStopHandsBack() throws Exception {
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    enqueue("3:7");
    enqueue("3:8");
    // a dead app left one running
    om(m -> m.claim(OutboxType.SECTOR_DELETE, 1, "dead-app"));
    assertEquals(1, (int) om(m -> m.count(null, OutboxStatus.RUNNING)));

    var runner = new OutboxRunner(cfg(), factory);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    runner.register(OutboxType.SECTOR_DELETE, new Handler() {
      @Override
      public void handle(OutboxEntry entry) {
        started.countDown();
        try {
          // blocks until the stop interrupts it
          release.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    });
    runner.start();
    assertTrue(runner.hasStarted());
    assertEquals(0, (int) om(m -> m.count(null, OutboxStatus.RUNNING)));

    runner.poll();
    assertTrue(started.await(10, TimeUnit.SECONDS));
    assertEquals(1, (int) om(m -> m.count(null, OutboxStatus.RUNNING)));
    runner.stop();
    assertFalse(runner.hasStarted());
    // the interrupted command is pending again, without an attempt counted
    assertEquals(0, (int) om(m -> m.count(null, OutboxStatus.RUNNING)));
    assertEquals(2, (int) om(m -> m.count(null, OutboxStatus.PENDING)));
    for (var e : om(m -> m.list(null, null, null))) {
      assertEquals(0, e.getAttempts());
    }
  }

  @Test
  public void doiFlags() throws Exception {
    ObjectNode params = life.catalogue.api.jackson.ApiModule.MAPPER.createObjectNode();
    params.put("PUBLISH", true);
    params.put("CREATE", true);
    params.put("UPDATE", false);
    assertEquals(java.util.EnumSet.of(DoiAction.CREATE, DoiAction.PUBLISH), Outbox.doiActions(params));
    assertTrue(Outbox.doiActions(null).isEmpty());
    assertEquals("3:17", Outbox.sectorKey(Outbox.parseSectorKey("3:17")));
  }
}
