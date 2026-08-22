package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.gbif.nameparser.util.UnicodeUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.jupiter.api.Disabled;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class NameIndexImplIT {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;

  NamesIndexConfig.Store type;
  NamesIndexConfig cfg;
  NameIndex ni;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.stream(NamesIndexConfig.Store.values())
      .map(s -> new Object[]{s})
      .collect(Collectors.toList());
  }

  public NameIndexImplIT(NamesIndexConfig.Store type) {
    this.type = type;
  }

  @After
  public void stop() throws Exception {
    if (ni != null) {
      ni.close();
    }
    if (cfg.file != null) {
      FileUtils.deleteQuietly(cfg.file);
    }
  }

  void setupMemory(boolean erase) {
    if (erase) {
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        session.getMapper(NamesIndexMapper.class).truncate();
      }
    }
    cfg = NamesIndexConfig.memory(512);
    cfg.type = this.type;
    ni = NameIndexFactory.build(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started();
    if (erase) {
      assertEquals(0, ni.size());
    } else {
      assertEquals(4, ni.size());
    }
  }

  void setupPersistent() throws Exception {
    File fIdx = TempFile.directoryFile();
    if (fIdx.exists()) {
      FileUtils.deleteQuietly(fIdx);
    }
    cfg = NamesIndexConfig.file(fIdx, 512);
    cfg.type = this.type;
    ni = NameIndexFactory.build(cfg, SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started();
  }

  @Test
  @Disabled @Ignore("manual test for debugging highly concurrent read/writes")
  public void concurrency() throws Exception {
    setupPersistent();
    final int concurrency = 100;
    final int size = 10000;
    final List<Thread> threads = new ArrayList<>();
    try {
      System.out.println(String.format("Creating %s matching threads", concurrency));
      for (int idx = 1; idx <= concurrency; idx++) {
        var matcher = new ContinousMatcher(idx, idx == 2, ni, size / 10);
        var t = new Thread(matcher, "matcher-" + idx);
        threads.add(t);
        t.start();
      }

      StopWatch watch = new StopWatch();
      watch.start();
      System.out.println(String.format("Start adding %s names to the index", size));
      Name n = new Name();
      n.setRank(Rank.SPECIES);
      n.setType(NameType.SCIENTIFIC);
      for (int key = 1; key <= size; key++) {
        n.setGenus("Abies");
        n.setSpecificEpithet("alba" + key);
        n.setScientificName("Abies alba" + key);
        ni.match(n, true, false);

        if (key % 100 == 0) {
          System.out.println(String.format("Added %s names to the index", key));
        }
      }
      watch.stop();
      System.out.printf("Adding %s names to %s took %s\n", size, type, watch);

    } finally {
      for (var t : threads) {
        t.interrupt();
      }
    }
  }
  static class ContinousMatcher implements Runnable {
    private final int idx;
    private final boolean verbose;
    private final NameIndex nidx;
    private final int maxIdx;
    private long counter;

    ContinousMatcher(int idx, boolean verbose, NameIndex nidx, int maxIdx) {
      this.idx = idx;
      this.verbose = verbose;
      this.nidx = nidx;
      this.maxIdx = maxIdx;
    }

    @Override
    public void run() {
      Name n = new Name();
      n.setRank(Rank.SPECIES);
      n.setScientificName("Abies alba");
      int i = 0;
      while (!Thread.currentThread().isInterrupted()) {
        n.setScientificName("Abies alba" + i++);
        if (i > maxIdx) {
          i = 1;
        }
        NameMatch m = nidx.match(n, false, verbose);
        if (counter++ % 10000 == 0) {
          System.out.println(String.format("matcher %s matched %s names: %s", idx, counter, m));
        }
      }
    }
  }

  @Test
  public void infragenerics() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());
    prepareTestNames().forEach(nn -> ni.match(nn, true, true));
    // single-tier: all 5 variants of "Abies alba"/"Abies albus" (with/without authorship) stem to the
    // same canonical bucket - see NameIndexImplTest.stemming() for the identical assertion.
    assertEquals(1, ni.size());

    Name n = new Name();
    n.setScientificName("Abies (Abies) alba");
    n.setGenus("Abies");
    n.setInfragenericEpithet("Abies");
    n.setSpecificEpithet("alba");
    n.setAuthorship("Mill.");
    n.setCombinationAuthorship(Authorship.authors("Mill."));
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);

    var m = ni.match(n, true, true);
    // single-tier: the canonical of a binomial ignores its (redundant) subgenus placement, so this
    // resolves to the very same "Abies alba" canonical entry.
    assertTrue(m.isMatched());
    final int canonNidx = m.getNidx();

    n = new Name();
    n.setScientificName("Abies (Pinus) alba");
    n.setGenus("Abies");
    n.setInfragenericEpithet("Pinus");
    n.setSpecificEpithet("alba");
    n.setAuthorship("Mill.");
    n.setCombinationAuthorship(Authorship.authors("Mill."));
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);

    m = ni.match(n, true, true);
    assertEquals(canonNidx, (int) m.getNidx());

    m = assertMatch(canonNidx, "Abies alba", Rank.UNRANKED);
    assertEquals(canonNidx, (int) m.getNidx());

    // new insert
    n = new Name();
    n.setScientificName("Pinella (Argworta) hansa");
    n.setGenus("Pinella");
    n.setInfragenericEpithet("Argworta");
    n.setSpecificEpithet("hansa");
    n.setAuthorship("DC.");
    n.setCombinationAuthorship(Authorship.authors("DC."));
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);

    m = ni.match(n, true, true);
    // single-tier: a genuinely new canonical name inserts a single fresh entry
    assertTrue(m.isMatched());
    assertNotEquals(canonNidx, (int) m.getNidx());
  }

  void setupNames(List<SimpleName> names) {
    setupMemory(true);
    assertEquals(0, ni.size());

    for (var sn : names) {
      var n = NameParser.PARSER.parse(sn).get().getName();
      n.setRank(sn.getRank()); // dont use interpreted ranks!
      var m = ni.match(n, true, true);
      assertTrue(m.isMatched());
    }
  }

  @Test
  public void poecile() throws Exception {
    setupNames(List.of(
      SimpleName.sn(Rank.SUBSPECIES, "Poecile montanus affinis", "Prjevalsky, 1876"),
      SimpleName.sn(Rank.SUBSPECIES, "Poecile montana affinis", "Prjevalsky, 1876"),
      SimpleName.sn(Rank.UNRANKED, "Poecile montana affinis", null),
      SimpleName.sn(Rank.SPECIES, "Poecile montana", "(Conrad von Baldenstein, 1827)"),
      SimpleName.sn(Rank.SUBSPECIES, "Poecile montana borealis", "Selys-Longchamps, 1843"),
      SimpleName.sn(Rank.SUBSPECIES, "Poecile montana kleinschmidti", "Hellmayr, 1900"),
      SimpleName.sn(Rank.SUBSPECIES, "Poecile montana songarus", "(Severtsov, 1873)"),
      SimpleName.sn(Rank.SUBSPECIES, "Poecile montana songarus", null),
      SimpleName.sn(Rank.UNRANKED, "Poecile montanus", null)
    ));

    dumpIndex();
    // single-tier: authorship/rank spellings collapse, and "montanus"/"montana" stem to the same
    // bucket, leaving 5 distinct canonical buckets. Every entry is canonical, so size == canonical count.
    assertEquals(5, ni.size());
  }

  @Test
  public void authorYears() throws Exception {
    setupNames(List.of(
      SimpleName.sn(Rank.SUBSPECIES, "Poa montanus affinis", "Prjevalsky, 1876"),
      SimpleName.sn(Rank.SUBSPECIES, "Poa montana affinis", "Prjevalsky, 1873"),
      SimpleName.sn(Rank.SPECIES, "Poa montana", "(Conrad von Baldenstein, 1827)"),
      SimpleName.sn(Rank.SPECIES, "Poa montana", "(Conrad von Baldenstein, 1837)"),
      SimpleName.sn(Rank.SPECIES, "Poa montana", "(Baldenstein, 1827)"),
      SimpleName.sn(Rank.SPECIES, "Poa montana", "(Baldenbrooks, 1829)"),
      SimpleName.sn(Rank.VARIETY, "Biota orientalis var. elegantissima", "Rollison ex Gordon"),
      SimpleName.sn(Rank.VARIETY, "Biota orientalis var. elegantissima", "Rollisson ex Godr.")
    ));

    dumpIndex();
    // single-tier: authorship/year is ignored, and "montanus"/"montana" stem to the same bucket,
    // leaving 3 distinct canonical buckets. Every entry is canonical, so size == canonical count.
    assertEquals(3, ni.size());
  }

  @Test
  public void loadApple() throws Exception {
    setupMemory(false);
    assertMatch(4, "Larus erfundus", Rank.SPECIES);
    assertMatch(4, "Larus erfunda", Rank.SPECIES);
    assertMatch(3, "Larus fusca", Rank.SPECIES);
    assertMatch(3, "Larus fuscus", Rank.SPECIES);
  }

  public static List<Name> prepareTestNames() {
    Name n1 = new Name();
    n1.setScientificName("Abies alba");
    n1.setGenus("Abies");
    n1.setSpecificEpithet("alba");
    n1.setAuthorship("Mill.");
    n1.setCombinationAuthorship(Authorship.authors("Mill."));
    n1.setRank(Rank.SPECIES);
    n1.setType(NameType.SCIENTIFIC);

    Name n2 = new Name(n1); // should be a variant and no new index name
    n2.setSpecificEpithet("albus");
    n2.setScientificName("Abies albus");

    Name n3 = new Name(n1); // should be the canonical and no new index name
    n3.setAuthorship(null);
    n3.setCombinationAuthorship(null);

    Name n4 = new Name(n2); // should be the canonical and no new index name
    n4.setAuthorship(null);
    n4.setCombinationAuthorship(null);

    Name n5 = new Name(n1); // should be the same
    n1.setAuthorship("Miller");
    n1.setCombinationAuthorship(Authorship.authors("Miller"));

    return List.of(n1, n2, n3, n4, n5);
  }

  @Test
  public void sequentialMatching() throws Exception {
    var names = prepareTestNames();
    final int repeat = names.size() * 2;

    sequentialMatching(names, repeat);

    // now also in reverse ordering
    var namesRev = new ArrayList<>(names);
    Collections.reverse(namesRev);
    sequentialMatching(namesRev, repeat);

    // manual order as failed in concurrent matching
    var namesAlt = List.of(names.get(2), names.get(1), names.get(0), names.get(4), names.get(3));
    sequentialMatching(namesAlt, repeat);
  }

  public void sequentialMatching(List<Name> rawNames, int repeat) throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

    final AtomicInteger counter = new AtomicInteger(0);

    for (int x = 0; x < repeat; x++) {
      Name n = rawNames.get(x % rawNames.size());
      counter.incrementAndGet();
      var m = ni.match(n, true, true);
      assertTrue(m.isMatched());
    }

    dumpIndex();
    assertEquals(repeat, counter.get());
    // single-tier: all 5 variants of "Abies alba"/"Abies albus" collapse onto the single canonical entry
    assertEquals(1, ni.size());
    assertCanonicalAbiesAlba();
    ni.close();
  }

  /**
   * Try to add the same name concurrently, making sure we never get duplicates in the index
   */
  @Test
  public void concurrentMatching() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

    final List<Name> rawNames = prepareTestNames();
    final AtomicInteger counter = new AtomicInteger(0);
    ExecutorService exec = Executors.newFixedThreadPool(52, new NamedThreadFactory("test-matcher"));

    final int repeat = rawNames.size() * 60;
    StopWatch watch = StopWatch.createStarted();
    for (int x = 0; x < repeat; x++) {
      Name n = rawNames.get(x % rawNames.size());
      CompletableFuture.supplyAsync(() -> {
        counter.incrementAndGet();
        return ni.match(n, true, true);
      }, exec).exceptionally(ex -> {
        ex.printStackTrace();
        return null;
      }).thenAccept(m -> {
        if (m == null) {
          fail("Matching error");
        }
        assertTrue(m.isMatched());
      });
    }
    ExecutorUtils.shutdown(exec);
    watch.stop();
    System.out.println(watch);

    dumpIndex();
    assertEquals(repeat, counter.get());
    // single-tier: all 5 variants of "Abies alba"/"Abies albus" collapse onto the single canonical entry
    assertEquals(1, ni.size());
    assertCanonicalAbiesAlba();
  }

  /**
   * Concurrent rebuilds run as separate processes/instances sharing one postgres, each with its own
   * heap store and JVM-local insertLock. Neither of those single-JVM safeguards can prevent two
   * independent instances from both observing a miss and racing to insert the same brand new
   * canonical name at the same time. The assign-on-miss insert therefore has to be atomic at the
   * database level: only one names_index row must ever exist for a given normalized key, and both
   * instances must end up pointing at that very same row.
   */
  @Test
  public void concurrentAssignAcrossInstances() throws Exception {
    setupMemory(true);
    final NameIndex ni1 = ni;

    NamesIndexConfig cfg2 = NamesIndexConfig.memory(512);
    cfg2.type = this.type;
    NameIndex ni2 = NameIndexFactory.build(cfg2, SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started();
    try {
      assertEquals(0, ni2.size());

      Name n1 = new Name();
      n1.setScientificName("Concurrentia testensis");
      n1.setGenus("Concurrentia");
      n1.setSpecificEpithet("testensis");
      n1.setRank(Rank.SPECIES);
      n1.setType(NameType.SCIENTIFIC);
      Name n2 = new Name(n1);

      final CyclicBarrier barrier = new CyclicBarrier(2);
      ExecutorService exec = Executors.newFixedThreadPool(2, new NamedThreadFactory("test-concurrent-instances"));
      Callable<NameMatch> task1 = () -> {
        barrier.await();
        return ni1.match(n1, true, true);
      };
      Callable<NameMatch> task2 = () -> {
        barrier.await();
        return ni2.match(n2, true, true);
      };
      Future<NameMatch> f1 = exec.submit(task1);
      Future<NameMatch> f2 = exec.submit(task2);
      NameMatch m1 = f1.get(30, TimeUnit.SECONDS);
      NameMatch m2 = f2.get(30, TimeUnit.SECONDS);
      ExecutorUtils.shutdown(exec);

      assertTrue("Expected instance 1 to match/insert the new name", m1.isMatched());
      assertTrue("Expected instance 2 to match/insert the new name", m2.isMatched());
      assertEquals("Both independent instances must resolve to the very same names_index key",
          m1.getNidx(), m2.getNidx());

      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        assertEquals("Concurrent assign-on-miss from two instances must create exactly one names_index row",
            1, session.getMapper(NamesIndexMapper.class).count());
      }
    } finally {
      ni2.close();
    }
  }

  void dumpIndex() {
    System.out.println("\nNames Index from postgres:");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(NamesIndexMapper.class).processAll().forEach(System.out::println);
    }
  }

  /**
   * The names index is single-tier & canonical-only: matching every authorship/rank variant of
   * "Abies alba"/"Abies albus" (see {@link #prepareTestNames()}) must resolve to the very same
   * single canonical entry - key 1 - and never create a second, rank/author specific entry.
   */
  public void assertCanonicalAbiesAlba() throws Exception {
    NameIndexEntry n1 = ni.get(1);
    assertNotNull(n1);
    assertNotNull(n1.getKey());
    // single-tier: no separate rank/author child entry is ever created
    assertNull(ni.get(2));
  }

  @Test
  public void truncate() throws Exception {
    setupPersistent();
    var m = ni.match(create("Abies", "alba", null, "Miller"), true, false);
    int millerKey = m.getNidx();
    // single-tier: "Duller" is just a different authorship of the very same canonical "Abies alba" -
    // it reuses the existing canonical entry rather than creating a separate row
    ni.match(create("Abies", "alba", null, "Duller"), true, false);
    // setupPersistent() reloads the apple fixture's 4 pre-existing canonical entries, plus the single
    // new "Abies alba" canonical added above
    assertEquals(5, ni.size());
    NameIndexEntry n = ni.get(millerKey);
    // single-tier: no authorship is ever kept on a names index entry - the carrier has no such field
    assertEquals("Abies alba", n.getScientificName());

    String epi = "alba";
    for (int i = 0; i < 100; i++) {
      epi = StringUtils.increase(epi);
      ni.match(create("Abies", epi, "" + (1800 + i) % 200, "Döring"), true, false);
    }

    ni.reset();
    assertEquals(0, ni.size());

    var m2 = ni.match(create("Abies", "alba", null, "Miller"), true, false);
    int miller2Key = m2.getNidx();
    // reuses the same canonical entry created above - no separate row
    ni.match(create("Abies", "alba", null, "Duller"), true, false);
    n = ni.get(miller2Key);
    assertEquals("Abies alba", n.getScientificName());
    assertEquals(1, ni.size());
  }

  private static Name create(String genus, String species, String year, String... authors) {
    Name n = new Name();
    if (authors != null || year != null) {
      n.setCombinationAuthorship(Authorship.yearAuthors(year, authors));
    }
    n.setGenus(genus);
    n.setSpecificEpithet(species);
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);
    n.rebuildScientificName();
    n.rebuildAuthorship();

    return n;
  }

  @Test
  public void restart() throws Exception {
    setupPersistent();

    assertMatch(4, "Larus erfundus", Rank.SPECIES);

    System.out.println("RESTART");
    assertEquals(4, ni.size());
    ni.stop();
    ni.start();
    assertEquals(4, ni.size());
    assertMatch(4, "Larus erfundus", Rank.SPECIES);

    ni.stop();
    try {
      match("Larus erfundus", Rank.SPECIES);
      fail("Names index is closed and should not return");
    } catch (UnavailableException e) {
      // expected!
    }
  }

  /**
   * Postgres is append-only and can grow between two starts of the very same persistent index - e.g.
   * another process inserted a new canonical name while this index instance was stopped. start() must
   * catch up just that delta (ids beyond the store's current max key) rather than clearing and
   * reloading everything, which would be far too slow once the names index holds tens of millions of
   * entries.
   */
  @Test
  public void catchUpOnRestart() throws Exception {
    setupPersistent();
    assertMatch(4, "Larus erfundus", Rank.SPECIES);
    assertEquals(4, ni.size());
    assertEquals(4, ni.store().maxKey());

    // simulate another process appending a brand new canonical name directly to postgres while this
    // index is stopped - bypasses NameIndexImpl entirely, so the persistent store has no way of
    // knowing about it other than catching up from postgres on the next start(). Persist the very same
    // normalized bucket key the matcher computes, so catch-up loads it under a matchable key.
    NameIndexEntry newName = new NameIndexEntry();
    newName.setScientificName("Catchupia testensis");
    newName.setNormalized(bucketKey(newName.getScientificName()));
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(NamesIndexMapper.class).create(newName);
    }
    assertTrue("new postgres row should get an id beyond the store's current max",
        newName.getKey() > ni.store().maxKey());

    int pgCount;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      pgCount = session.getMapper(NamesIndexMapper.class).count();
    }
    assertEquals(5, pgCount);

    // restart against the SAME persistent store file, but through a fresh instance configured with
    // verification disabled
    ni.stop();
    NamesIndexConfig cfg2 = NamesIndexConfig.file(cfg.file, 512);
    cfg2.type = this.type;
    cfg2.verification = false;
    cfg = cfg2;
    ni = NameIndexFactory.build(cfg2, SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started();

    assertEquals(5, ni.size());
    assertEquals(5, ni.store().maxKey());
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      assertEquals(session.getMapper(NamesIndexMapper.class).count(), ni.store().count());
    }
    // the delta row is now matchable ...
    assertMatch(newName.getKey(), "Catchupia testensis", Rank.SPECIES);
    // ... and the pre-existing entries survived the restart untouched
    assertMatch(4, "Larus erfundus", Rank.SPECIES);
  }

  /**
   * Recomputes the exact normalized bucket key {@code NameIndexImpl.key} uses to bucket a canonical name.
   */
  private static String bucketKey(String scientificName) {
    return UnicodeUtils.replaceNonAscii(SciNameNormalizer.normalize(UnicodeUtils.decompose(scientificName)).toLowerCase(), '*');
  }

  static Name name(String name, Rank rank) throws InterruptedException {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName());
    n.setRank(rank);
    return n;
  }

  private NameMatch assertMatch(int key, String name, Rank rank) throws InterruptedException {
    NameMatch m = match(name, rank);
    if (!m.isMatched() || key != m.getNidx()) {
      System.err.println(m);
    }
    assertTrue("Expected single match but got none", m.isMatched());
    assertEquals("Expected " + key + " but got " + m.getNidx(), key, (int) m.getNidx());
    return m;
  }

  private NameMatch match(String name, Rank rank) throws InterruptedException {
    return ni.match(name(name, rank), false, true);
  }

}
