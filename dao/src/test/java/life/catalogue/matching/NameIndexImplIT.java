package life.catalogue.matching;

import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.ArchivedNameUsageMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;
import life.catalogue.parser.NameParser;

import life.catalogue.printer.TxtTreeDataRule;

import org.apache.commons.io.FileUtils;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
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
      //dumpIndex();
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
  @Ignore("manual test for debugging highly concurrent read/writes")
  public void concurrency() throws Exception {
    setupPersistent();
    final int concurrency = 100;
    final int size = 10000;
    // setup some concurrent matcher reading data
    final List<Thread> threads = new ArrayList<>();
    try {
      System.out.println(String.format("Creating %s matching threads", concurrency));
      for (int idx=1; idx <=concurrency; idx++) {
        var matcher = new ContinousMatcher(idx, idx==2, ni, size/10);
        var t = new Thread(matcher, "matcher-"+idx);
        threads.add(t);
        t.start();
      }

      StopWatch watch = new StopWatch();
      var pn = NameParser.PARSER.parse("Abies alba Mill.", Rank.SPECIES, NomCode.BOTANICAL, IssueContainer.VOID);
      final IndexName n = new IndexName(pn.get().getName());
      watch.start();
      System.out.println(String.format("Start adding %s names to the index", size));
      for (int key = 1; key <= size; key++) {
        n.setKey(key);
        n.setCanonicalId(key / 10);
        n.setScientificName("Abies alba"+key);
        n.setSpecificEpithet("alba"+key);
        ni.add(n);

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
        n.setScientificName("Abies alba"+i++);
        if (i>maxIdx) {
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
    assertEquals(3, ni.size());

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
    assertEquals(MatchType.VARIANT, m.getType());
    final int spNidx = m.getNameKey();
    final int canonNidx = m.getCanonicalNameKey();
    assertNotEquals(canonNidx, spNidx);

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
    assertEquals(MatchType.VARIANT, m.getType());
    assertEquals(canonNidx, (int) m.getCanonicalNameKey());
    assertEquals(spNidx, (int) m.getNameKey());

    m = assertMatch(canonNidx, "Abies alba", Rank.UNRANKED);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(canonNidx, (int) m.getNameKey());
    assertEquals(canonNidx, (int) m.getCanonicalNameKey());


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
    assertEquals(MatchType.VARIANT, m.getType());
    assertNotEquals(m.getCanonicalNameKey(), m.getNameKey());
  }

  void setupNames(List<SimpleName> names) {
    setupMemory(true);
    assertEquals(0, ni.size());

    for (var sn : names) {
      var n = new Name();
      n.setScientificName(sn.getName());
      n.setAuthorship(sn.getAuthorship());
      n.setRank(sn.getRank());
      n.setType(NameType.SCIENTIFIC);

      var m = ni.match(n, true, true);
      assertTrue(m.getType() == MatchType.EXACT || m.getType() == MatchType.VARIANT);
    }

    assertAllUnique();
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
    assertEquals(11, ni.size());
    assertCanonicalSize(5);
  }

  private void assertAllUnique() {
    var names = new HashSet<>();
    for (var n : ni.all()) {
      var sn = new SimpleName(null, n.getScientificName(), n.getAuthorship(), n.getRank());
      if (!names.add(sn)) {
        dumpIndex();
        throw new IllegalStateException("Non unique name "+sn+" in names index");
      }
    }
  }

  private int assertCanonicalSize(int num) {
    int count = 0;
    for (var n : ni.all()) {
      if (n.isCanonical()) {
        count++;
      }
    }
    assertEquals(num, count);
    return count;
  }

  @Test
  public void loadApple() throws Exception {
    setupMemory(false);
    assertMatch(4, "Larus erfundus", Rank.SPECIES);
    assertMatch(4, "Larus erfunda", Rank.SPECIES);
    assertMatch(3, "Larus fusca", Rank.SPECIES);
    assertMatch(3, "Larus fuscus", Rank.SPECIES);
  }

  @Test
  public void delete() throws Throwable {
    setupMemory(false);
    assertEquals("Apia apis", ni.get(1).getLabel());
    assertEquals("Malus sylvestris", ni.get(2).getLabel());
    assertEquals(4, ni.size());

    ni.delete(1, false);
    assertEquals(3, ni.size());
    assertNull(ni.get(1));
    assertEquals("Malus sylvestris", ni.get(2).getLabel());

    ni.delete(2, true);
    assertNull(ni.get(1));
    assertNull(ni.get(2));
    assertEquals(4, ni.size());

    List<TxtTreeDataRule.TreeDataset> data = new ArrayList<>();
    data.add(new TxtTreeDataRule.TreeDataset(101, "trees/nidx1.tree"));
    data.add(new TxtTreeDataRule.TreeDataset(102, "trees/nidx2.tree"));
    data.add(new TxtTreeDataRule.TreeDataset(103, "trees/nidx3.tree"));
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(data)) {
      treeRule.before();
    }
    rematchAll();
    //dumpIndex();

    int nidxSize = 25;
    assertEquals(nidxSize, ni.size());
    var m = ni.match(Name.build("Abbella zabinskii", "Novicki, 1936", Rank.SPECIES), false, false);
    var group = ni.byCanonical(m.getCanonicalNameKey());
    ni.delete(m.getCanonicalNameKey(), false);
    assertNull(ni.get(m.getNameKey()));
    assertNull(ni.get(m.getCanonicalNameKey()));
    for (var n : group) {
      assertNull(ni.get(n.getKey()));
    }
    assertEquals(nidxSize-group.size()-1, ni.size());

    // once more with rematching
    rematchAll();
    assertEquals(nidxSize, ni.size());
    m = ni.match(Name.build("Abbella zabinskii", "Novicki, 1936", Rank.SPECIES), false, false);
    group = ni.byCanonical(m.getCanonicalNameKey());
    ni.delete(m.getCanonicalNameKey(), true);
    // same index size, but new keys!
    assertEquals(nidxSize, ni.size());
    assertNull(ni.get(m.getNameKey()));
    assertNull(ni.get(m.getCanonicalNameKey()));
    for (var n : group) {
      assertNull(ni.get(n.getKey()));
    }
  }

  void rematchAll() {
    IntSet keys;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      keys = DaoUtils.listDatasetWithNames(session);
      keys.addAll(
        session.getMapper(ArchivedNameUsageMapper.class).listProjects()
      );
    }
    System.out.println("Rematch all "+keys.size()+" datasets with data using a names index of size " + ni.size());
    RematchJob.some(Users.MATCHER, SqlSessionFactoryRule.getSqlSessionFactory(), ni, null, false, keys.toIntArray()).run();
    System.out.println("Rematch done. New names index size = " + ni.size());
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
    final int repeat = names.size()*2;

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

    for (int x=0; x<repeat; x++) {
      Name n = rawNames.get(x % rawNames.size());
      counter.incrementAndGet();
      var m = ni.match(n, true, true);
      assertTrue(m.hasMatch());
      final Integer idx = m.getName().getKey();
      final Integer cidx = m.getName().getCanonicalId();
      if (n.hasAuthorship() || n.getRank() != Rank.UNRANKED) {
        assertNotEquals(idx, cidx);
      } else {
        assertEquals(idx, cidx);
      }
    }

    dumpIndex();
    assertEquals(repeat, counter.get());
    assertEquals(3, ni.size());
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
    for (int x=0; x<repeat; x++) {
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
        assertTrue(m.hasMatch());
        final Integer idx = m.getName().getKey();
        final Integer cidx = m.getName().getCanonicalId();
        if (n.hasAuthorship()) {
          assertNotEquals(idx, cidx);
        } else {
          assertEquals(idx, cidx);
        }
      });
    }
    ExecutorUtils.shutdown(exec);
    watch.stop();
    System.out.println(watch);

    dumpIndex();
    assertEquals(repeat, counter.get());
    assertEquals(3, ni.size());
    assertCanonicalAbiesAlba();
  }

  void dumpIndex() {
    System.out.println("\nNames Index from postgres:");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(NamesIndexMapper.class).processAll().forEach(System.out::println);
    }
  }

  public void assertCanonicalAbiesAlba() throws Exception {
    IndexName n1 = ni.get(1);
    assertTrue(n1.isCanonical());

    IndexName n2 = ni.get(2);
    assertNotEquals(n1, n2);

    assertEquals(n1.getCanonicalId(), n2.getCanonicalId());
    assertEquals(n1.getKey(), n2.getCanonicalId());
    var group = ni.byCanonical(n1.getCanonicalId());
    assertEquals(2, group.size());
    for (var n : group) {
      assertFalse(n.isCanonical());
      assertEquals(n1.getKey(), n.getCanonicalId());
    }
  }

  @Test
  public void truncate() throws Exception {
    setupPersistent();
    ni.add(create("Abies", "alba", null, "Miller"));
    ni.add(create("Abies", "alba", null, "Duller"));
    assertEquals(7, ni.size());
    IndexName n = ni.get(7);
    assertEquals("Abies alba", n.getScientificName());
    assertEquals("Duller", n.getAuthorship());

    String epi = "alba";
    for (int i = 0; i<100; i++) {
      epi = StringUtils.increase(epi);
      ni.add(create("Abies", epi, ""+(1800+i)%200, "Döring"));
    }

    ni.reset();
    assertEquals(0, ni.size());

    ni.add(create("Abies", "alba", null, "Miller"));
    ni.add(create("Abies", "alba", null, "Duller"));
    n = ni.get(2);
    assertEquals(3, ni.size());
  }

  private static IndexName create(String genus, String species, String year, String... authors){
    Name n = new Name();
    if (authors != null || year != null) {
      n.setCombinationAuthorship(Authorship.yearAuthors(year, authors));
    }
    n.setGenus(genus);
    n.setSpecificEpithet(species);
    n.setRank(Rank.SPECIES);
    n.rebuildScientificName();
    n.rebuildAuthorship();

    return new IndexName(n);
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


  static Name name(String name, Rank rank) throws InterruptedException {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName());
    n.setRank(rank);
    return n;
  }

  static IndexName iname(String name, Rank rank) throws InterruptedException {
    return new IndexName(name(name, rank));
  }

  private NameMatch assertMatchType(MatchType expected, String name, Rank rank) throws InterruptedException {
    NameMatch m = match(name, rank);
    if (expected != m.getType()) {
      System.out.println(m);
    }
    assertEquals("No match expected but got " + m.getType(),
        expected, m.getType()
    );
    return m;
  }

  private NameMatch assertMatch(int key, String name, Rank rank) throws InterruptedException {
    return assertMatch(key, null, name, rank);
  }

  private NameMatch assertMatch(int key, MatchType type, String name, Rank rank) throws InterruptedException {
    NameMatch m = match(name, rank);
    if (!m.hasMatch() || key != m.getName().getKey()) {
      System.err.println(m);
    }
    assertTrue("Expected single match but got none", m.hasMatch());
    assertEquals("Expected " + key + " but got " + m.getType(), key, (int) m.getName().getKey());
    if (type != null) {
      assertEquals("Expected " + type + " but got " + m.getType(), type, m.getType());
    }
    return m;
  }

  private NameMatch match(String name, Rank rank) throws InterruptedException {
    NameMatch m = ni.match(name(name, rank), false, true);
    return m;
  }
  
}