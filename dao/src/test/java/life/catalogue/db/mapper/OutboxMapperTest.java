package life.catalogue.db.mapper;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.OutboxStatus;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.api.vocab.Users;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.Assert.*;

public class OutboxMapperTest extends MapperTestBase<OutboxMapper> {
  static final String A = "runner-a";
  static final String B = "runner-b";

  public OutboxMapperTest() {
    super(OutboxMapper.class, TestDataRule.empty());
  }

  /**
   * Every runner statement is an autocommit statement of its own, so run them that way here too.
   */
  static <T> T om(Function<OutboxMapper, T> action) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      return action.apply(session.getMapper(OutboxMapper.class));
    }
  }

  static ObjectNode flag(String name) {
    ObjectNode params = ApiModule.MAPPER.createObjectNode();
    params.put(name, true);
    return params;
  }

  static void enqueue(String key, String flag, int delaySeconds) {
    om(m -> {
      m.enqueue(OutboxType.DOI, key, flag(flag), delaySeconds, Users.TESTER);
      return null;
    });
  }

  static OutboxEntry get(String key) {
    return om(m -> m.getByKey(OutboxType.DOI, key));
  }

  @Test
  public void enqueueMerges() throws Exception {
    enqueue("10.80631/a", "UPDATE", 300);
    var e = get("10.80631/a");
    assertEquals(OutboxStatus.PENDING, e.getStatus());
    assertEquals(0, e.getVersion());
    assertEquals(Users.TESTER, (int) e.getCreatedBy());
    assertTrue(e.getDue().isAfter(LocalDateTime.now().plusSeconds(200)));
    final var firstDue = e.getDue();

    // a later update keeps the earlier due time, so a burst is sent together
    enqueue("10.80631/a", "UPDATE", 300);
    e = get("10.80631/a");
    assertEquals(1, e.getVersion());
    assertEquals(firstDue, e.getDue());

    // a create is due now and pulls the whole command forward
    enqueue("10.80631/a", "CREATE", 0);
    e = get("10.80631/a");
    assertEquals(2, e.getVersion());
    assertTrue(e.getParams().path("UPDATE").asBoolean());
    assertTrue(e.getParams().path("CREATE").asBoolean());
    assertFalse(e.getDue().isAfter(LocalDateTime.now().plusSeconds(1)));

    assertEquals(1, (int) om(m -> m.count(OutboxType.DOI, null)));
    assertEquals(0, (int) om(m -> m.count(OutboxType.SECTOR_DELETE, null)));
  }

  @Test
  public void claimOnlyDue() throws Exception {
    enqueue("10.80631/later", "UPDATE", 300);
    enqueue("10.80631/now", "CREATE", 0);
    var claimed = om(m -> m.claim(OutboxType.DOI, 10, A));
    assertEquals(1, claimed.size());
    var c = claimed.get(0);
    assertEquals("10.80631/now", c.getKey());
    assertEquals(OutboxStatus.RUNNING, c.getStatus());
    assertEquals(A, c.getClaimedBy());
    // claimed rows are not claimed again
    assertTrue(om(m -> m.claim(OutboxType.DOI, 10, B)).isEmpty());
    // nor are rows of another type
    assertTrue(om(m -> m.claim(OutboxType.SECTOR_DELETE, 10, B)).isEmpty());
  }

  @Test
  public void concurrentClaimsAreDisjoint() throws Exception {
    final int n = 40;
    for (int i = 0; i < n; i++) {
      enqueue("10.80631/c" + i, "CREATE", 0);
    }
    ExecutorService exec = Executors.newFixedThreadPool(4);
    try {
      List<Future<List<OutboxEntry>>> futures = new ArrayList<>();
      for (int t = 0; t < 8; t++) {
        final String instance = "runner-" + t;
        futures.add(exec.submit(() -> om(m -> m.claim(OutboxType.DOI, 7, instance))));
      }
      Set<Long> ids = new HashSet<>();
      int total = 0;
      for (var f : futures) {
        var claimed = f.get(30, TimeUnit.SECONDS);
        total += claimed.size();
        claimed.forEach(e -> ids.add(e.getId()));
      }
      assertEquals(n, total);
      assertEquals(n, ids.size());
    } finally {
      exec.shutdownNow();
    }
  }

  @Test
  public void completeAndRequeueAfterMerge() throws Exception {
    enqueue("10.80631/x", "CREATE", 0);
    enqueue("10.80631/y", "CREATE", 0);
    var claimed = om(m -> m.claim(OutboxType.DOI, 10, A));
    assertEquals(2, claimed.size());
    var x = claimed.stream().filter(e -> e.getKey().equals("10.80631/x")).findFirst().orElseThrow();
    var y = claimed.stream().filter(e -> e.getKey().equals("10.80631/y")).findFirst().orElseThrow();

    // another runner cannot complete it
    assertEquals(0, (int) om(m -> m.complete(x.getId(), x.getVersion(), B)));
    assertEquals(1, (int) om(m -> m.complete(x.getId(), x.getVersion(), A)));
    assertNull(get("10.80631/x"));

    // a request merged in while y runs keeps it running, but the run can no longer complete it
    enqueue("10.80631/y", "PUBLISH", 0);
    var y2 = get("10.80631/y");
    assertEquals(OutboxStatus.RUNNING, y2.getStatus());
    assertEquals(y.getVersion() + 1, y2.getVersion());
    assertEquals(0, (int) om(m -> m.complete(y.getId(), y.getVersion(), A)));
    assertEquals(1, (int) om(m -> m.requeue(y.getId(), A)));
    y2 = get("10.80631/y");
    assertEquals(OutboxStatus.PENDING, y2.getStatus());
    assertNull(y2.getClaimedBy());
    assertTrue(y2.getParams().path("CREATE").asBoolean());
    assertTrue(y2.getParams().path("PUBLISH").asBoolean());
    assertEquals(1, om(m -> m.claim(OutboxType.DOI, 10, A)).size());
  }

  @Test
  public void retryAndFail() throws Exception {
    enqueue("10.80631/r", "CREATE", 0);
    var r = om(m -> m.claim(OutboxType.DOI, 1, A)).get(0);
    assertEquals(1, (int) om(m -> m.retry(r.getId(), A, 1, 3600, "boom")));
    var e = get("10.80631/r");
    assertEquals(OutboxStatus.PENDING, e.getStatus());
    assertEquals(1, e.getAttempts());
    assertEquals("boom", e.getLastError());
    assertNull(e.getClaimedBy());
    // not due before the delay
    assertTrue(om(m -> m.claim(OutboxType.DOI, 1, A)).isEmpty());

    // a retry later does not count an attempt
    om(m -> m.retryNow(e.getId()));
    var r2 = om(m -> m.claim(OutboxType.DOI, 1, A)).get(0);
    assertEquals(0, r2.getAttempts()); // retryNow resets attempts
    om(m -> m.retry(r2.getId(), A, 0, 0, "not now"));
    assertEquals(0, get("10.80631/r").getAttempts());

    // fail for good
    var r3 = om(m -> m.claim(OutboxType.DOI, 1, A)).get(0);
    assertEquals(1, (int) om(m -> m.fail(r3.getId(), r3.getVersion(), A, "rejected")));
    var f = get("10.80631/r");
    assertEquals(OutboxStatus.FAILED, f.getStatus());
    assertEquals(1, f.getAttempts());
    assertEquals("rejected", f.getLastError());
    assertTrue(om(m -> m.claim(OutboxType.DOI, 1, A)).isEmpty());
    assertEquals(1, (int) om(m -> m.count(null, OutboxStatus.FAILED)));

    // a new request revives a failed command
    enqueue("10.80631/r", "UPDATE", 0);
    f = get("10.80631/r");
    assertEquals(OutboxStatus.PENDING, f.getStatus());
    assertEquals(0, f.getAttempts());
    assertNull(f.getLastError());
    assertTrue(f.getParams().path("CREATE").asBoolean());
    assertTrue(f.getParams().path("UPDATE").asBoolean());
  }

  @Test
  public void failAfterMergeStaysPending() throws Exception {
    enqueue("10.80631/m", "CREATE", 0);
    var m1 = om(m -> m.claim(OutboxType.DOI, 1, A)).get(0);
    enqueue("10.80631/m", "UPDATE", 0);
    om(m -> m.fail(m1.getId(), m1.getVersion(), A, "rejected"));
    var e = get("10.80631/m");
    assertEquals(OutboxStatus.PENDING, e.getStatus());
    assertEquals(0, e.getAttempts());
  }

  @Test
  public void releaseAndReset() throws Exception {
    enqueue("10.80631/1", "CREATE", 0);
    enqueue("10.80631/2", "CREATE", 0);
    assertEquals(1, om(m -> m.claim(OutboxType.DOI, 1, A)).size());
    assertEquals(1, om(m -> m.claim(OutboxType.DOI, 1, B)).size());
    assertEquals(2, (int) om(m -> m.count(null, OutboxStatus.RUNNING)));

    assertEquals(1, (int) om(m -> m.releaseMine(A)));
    assertEquals(1, (int) om(m -> m.count(null, OutboxStatus.RUNNING)));
    assertEquals(1, (int) om(m -> m.resetStale()));
    assertEquals(0, (int) om(m -> m.count(null, OutboxStatus.RUNNING)));
    assertEquals(2, (int) om(m -> m.count(OutboxType.DOI, OutboxStatus.PENDING)));
  }

  @Test
  public void listCountPurge() throws Exception {
    enqueue("10.80631/l1", "CREATE", 0);
    enqueue("10.80631/l2", "UPDATE", 300);
    om(m -> {
      m.enqueue(OutboxType.SECTOR_DELETE, "3:12", ApiModule.MAPPER.createObjectNode(), 0, Users.TESTER);
      return null;
    });
    assertEquals(3, om(m -> m.list(null, null, new Page())).size());
    var dois = om(m -> m.list(OutboxType.DOI, OutboxStatus.PENDING, new Page()));
    assertEquals(2, dois.size());
    // earliest due first
    assertEquals("10.80631/l1", dois.get(0).getKey());
    assertEquals(0, (int) om(m -> m.countOverdue(15, null)));

    // overdue and failed long ago
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getConnection().createStatement().execute(
        "UPDATE outbox SET due = now() - interval '1 hour' WHERE key = '10.80631/l1'");
      session.getConnection().createStatement().execute(
        "UPDATE outbox SET status = 'FAILED', modified = now() - interval '40 days' WHERE key = '3:12'");
    }
    assertEquals(1, (int) om(m -> m.countOverdue(15, null)));
    assertEquals(0, (int) om(m -> m.purgeFailed(50)));
    assertEquals(1, (int) om(m -> m.purgeFailed(30)));
    assertEquals(2, (int) om(m -> m.count(null, null)));

    var l2 = get("10.80631/l2");
    assertEquals(1, (int) om(m -> m.delete(l2.getId())));
    assertNull(get("10.80631/l2"));
  }
}
