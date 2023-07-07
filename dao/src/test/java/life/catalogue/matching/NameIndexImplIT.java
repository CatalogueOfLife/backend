package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameIndexImplIT {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;

  NameIndex ni;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @After
  public void stop() throws Exception {
    if (ni != null) {
      dumpIndex();
      ni.close();
    }
  }

  void addTestNames() throws Exception {
    List.of(
      // 1+2
      iname("Animalia", Rank.KINGDOM),
      // 3+4
      iname("Oenanthe Vieillot, 1816", Rank.GENUS),
      // 5
      iname("Oenanthe Pallas, 1771", Rank.GENUS),
      // 6
      iname("Oenanthe L.", Rank.GENUS),
      // 7
      iname("Oenanthe aquatica", Rank.SPECIES),
      // 8
      iname("Oenanthe aquatica Poir.", Rank.SPECIES),
      // 9
      iname("Oenanthe aquatica Senser, 1957", Rank.SPECIES),
      // 10
      iname("Natting tosee", Rank.SPECIES),
      // 11
      iname("Abies alba", Rank.SPECIES),
      // 12
      iname("Abies alba Mumpf.", Rank.SPECIES),
      // 13
      iname("Abies alba 1778", Rank.SPECIES),
      // 14+15
      iname("Picea alba 1778", Rank.SPECIES),
      // 16
      iname("Picea", Rank.GENUS),
      // 17
      iname("Carex cayouettei", Rank.SPECIES),
      // 18
      iname("Carex comosa × Carex lupulina", Rank.SPECIES),
      // 19
      iname("Natting tosee2", Rank.SPECIES),
      // 20
      iname("Natting tosee3", Rank.SPECIES),
      // 21
      iname("Natting tosee4", Rank.SPECIES),
      // 22
      iname("Natting tosee5", Rank.SPECIES),
      // 23
      iname("Rodentia", Rank.GENUS),
      // 24+25
      iname("Rodentia Bowdich, 1821", Rank.ORDER),
      // 26
      iname("Aeropyrum coil-shaped virus", Rank.UNRANKED)
    ).forEach(n -> {
      ni.add(n);
    });
    dumpIndex();
    assertEquals(26, ni.size());
  }

  void setupMemory(boolean erase) throws Exception {
    if (erase) {
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        session.getMapper(NamesIndexMapper.class).truncate();
      }
    }
    ni = NameIndexFactory.memory(SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer).started();
    if (erase) {
      assertEquals(0, ni.size());
    } else {
      assertEquals(4, ni.size());
    }
  }

  void setupPersistent(File location) throws Exception {
    ni = NameIndexFactory.persistent(location, SqlSessionFactoryRule.getSqlSessionFactory(), aNormalizer, true).started();
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
    File fIdx = File.createTempFile("col", ".nidx");
    if (fIdx.exists()) {
      fIdx.delete();
    }
    setupPersistent(fIdx);
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
    File fIdx = File.createTempFile("col", ".nidx");
    if (fIdx.exists()) {
      fIdx.delete();
    }

    try {
      setupPersistent(fIdx);

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

    } finally {
      fIdx.delete();
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