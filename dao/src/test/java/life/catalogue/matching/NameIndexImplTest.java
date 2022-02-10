package life.catalogue.matching;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.parser.NameParser;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.text.StringUtils;
import life.catalogue.concurrent.ExecutorUtils;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NamesIndexMapper;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.File;
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

public class NameIndexImplTest {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;

  NameIndex ni;
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @After
  public void stop() throws Exception {
    if (ni != null) {
      ni.close();
    }
  }

  void addTestNames() throws Exception {
    List.of(
      // 1
      iname("Animalia", Rank.KINGDOM),
      // 2+3
      iname("Oenanthe Vieillot, 1816", Rank.GENUS),
      // 4
      iname("Oenanthe Pallas, 1771", Rank.GENUS),
      // 5
      iname("Oenanthe L.", Rank.GENUS),
      // 6
      iname("Oenanthe aquatica", Rank.SPECIES),
      // 7
      iname("Oenanthe aquatica Poir.", Rank.SPECIES),
      // 8
      iname("Oenanthe aquatica Senser, 1957", Rank.SPECIES),
      // 9
      iname("Natting tosee", Rank.SPECIES),
      // 10
      iname("Abies alba", Rank.SPECIES),
      // 11
      iname("Abies alba Mumpf.", Rank.SPECIES),
      // 12
      iname("Abies alba 1778", Rank.SPECIES),
      // 13+14
      iname("Picea alba 1778", Rank.SPECIES),
      // 15
      iname("Picea", Rank.GENUS),
      // 16
      iname("Carex cayouettei", Rank.SPECIES),
      // 17
      iname("Carex comosa × Carex lupulina", Rank.SPECIES),
      // 18
      iname("Natting tosee2", Rank.SPECIES),
      // 19
      iname("Natting tosee3", Rank.SPECIES),
      // 20
      iname("Natting tosee4", Rank.SPECIES),
      // 21
      iname("Natting tosee5", Rank.SPECIES),
      // 22
      iname("Rodentia", Rank.GENUS),
      // 23
      iname("Rodentia Bowdich, 1821", Rank.ORDER),
      // 24
      iname("Aeropyrum coil-shaped virus", Rank.UNRANKED)
    ).forEach(n -> {
      ni.add(n);
    });
    assertEquals(24, ni.size());
  }

  void setupMemory(boolean erase) throws Exception {
    if (erase) {
      try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
        session.getMapper(NamesIndexMapper.class).truncate();
      }
    }
    ni = NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), aNormalizer).started();
    if (erase) {
      assertEquals(0, ni.size());
    } else {
      assertEquals(4, ni.size());
    }
  }

  void setupPersistent(File location) throws Exception {
    ni = NameIndexFactory.persistent(location, PgSetupRule.getSqlSessionFactory(), aNormalizer).started();
  }

  @Test
  public void loadApple() throws Exception {
    setupMemory(false);
    assertMatch(4, "Larus erfundus", Rank.SPECIES);
    assertMatch(4, "Larus erfunda", Rank.SPECIES);
    assertMatch(3, "Larus fusca", Rank.SPECIES);
    assertMatch(3, "Larus fuscus", Rank.SPECIES);
  }

  /**
   * Try to add the same name again and multiple names with the same key
   */
  @Test
  public void add() throws Exception {
    setupMemory(true);
    ni.add(create("Abies", "krösus-4-par∂atœs"));
    ni.add(create("Abies", "alba"));
    ni.add(create("Abies", "alba", "1873"));
    // 2 canonical, 1 with author
    assertEquals(3, ni.size());

    // one more with author, no new canonical
    ni.add(create("Abies", "alba", "1873", "Miller"));
    assertEquals(4, ni.size());

    // 2 new records
    ni.add(create("Abies", "perma", "1901", "Jones"));
    assertEquals(6, ni.size());
  }

  /**
   * match the same name over and over again to make sure we never insert duplicates
   */
  @Test
  public void avoidDuplicates() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

    Name n = new Name();
    n.setScientificName("Abies alba");
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setAuthorship("Mill.");
    n.setCombinationAuthorship(Authorship.authors("Mill."));
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);

    NameMatch m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    final Integer idx = m.getName().getKey();
    final Integer cidx = m.getName().getCanonicalId();
    assertNotEquals(idx, cidx);
    assertEquals(2, ni.size());

    m = ni.match(n, true, true);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(2, ni.size());

    m = ni.match(n, true, false);
    assertEquals(MatchType.EXACT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(2, ni.size());

    n.setAuthorship("Miller");
    n.setCombinationAuthorship(Authorship.authors("Miller"));
    m = ni.match(n, true, true);
    assertEquals(MatchType.VARIANT, m.getType());
    assertEquals(idx, m.getName().getKey());
    assertEquals(2, ni.size());

    n.setAuthorship("Tesla");
    n.setCombinationAuthorship(Authorship.authors("Tesla"));
    m = ni.match(n, true, true);
    final Integer idxTesla = m.getName().getKey();
    assertNotEquals(idx, cidx);
    assertEquals(MatchType.EXACT, m.getType());
    assertNotEquals(idx, idxTesla);
    assertEquals(cidx, m.getName().getCanonicalId());
    assertEquals(3, ni.size());
  }


  /**
   * Try to add the same name concurrently, making sure we never get duplicates in the index
   */
  @Test
  public void concurrentMatching() throws Exception {
    setupMemory(true);
    assertEquals(0, ni.size());

    Name n = new Name();
    n.setScientificName("Abies alba");
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setAuthorship("Mill.");
    n.setCombinationAuthorship(Authorship.authors("Mill."));
    n.setRank(Rank.SPECIES);
    n.setType(NameType.SCIENTIFIC);

    final AtomicInteger counter = new AtomicInteger(0);
    ExecutorService exec = Executors.newFixedThreadPool(12, new NamedThreadFactory("test-matcher"));

    final int repeat = 1000;
    StopWatch watch = StopWatch.createStarted();
    for (int x=0; x<repeat; x++) {
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
        assertEquals(MatchType.EXACT, m.getType());
        final Integer idx = m.getName().getKey();
        final Integer cidx = m.getName().getCanonicalId();
        assertNotEquals(idx, cidx);
        assertEquals(2, ni.size());
      });
    }
    ExecutorUtils.shutdown(exec);
    watch.stop();
    System.out.println(watch);

    assertEquals(repeat, counter.get());
    assertEquals(2, ni.size());
  }

  @Test
  public void getCanonical() throws Exception {
    setupMemory(true);
    ni.add(create("Abies", "alba", null, "Miller"));
    assertEquals(2, ni.size());

    IndexName n1 = ni.get(1);
    IndexName n2 = ni.get(2);
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


  private static IndexName create(String genus, String species){
    return create(genus, species, null);
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
  
  @Test
  public void insertNewNames() throws Exception {
    setupMemory(false);
    assertInsert("Larus fundatus", Rank.SPECIES);
    assertInsert("Puma concolor", Rank.SPECIES);
  }
  
  @Test
  public void testLookup() throws Exception {
    setupMemory(true);
    addTestNames();

    assertMatch(4, "Œnanthe 1771", Rank.GENUS);
    assertMatch(1, "Animalia", Rank.KINGDOM);

    assertMatch(22, "Rodentia", Rank.GENUS);
    assertNoMatch("Rodentia", Rank.ORDER);
    assertNoMatch("Rodenti", Rank.ORDER);
    
    assertMatch(23, "Rodentia Bowdich, 1821", Rank.ORDER);
    assertMatch(23, "Rodentia Bowdich, 1221", Rank.ORDER);
    assertMatch(23, "Rodentia Bowdich", Rank.ORDER);
    assertMatch(23, "Rodentia 1821", Rank.ORDER);
    assertMatch(23, "Rodentia Bow.", Rank.ORDER);
    assertMatch(23, "Rodentia Bow, 1821", Rank.ORDER);
    assertMatch(23, "Rodentia B 1821", Rank.ORDER);
    assertNoMatch("Rodentia", Rank.FAMILY);
    assertNoMatch("Rodentia Mill., 1823", Rank.SUBORDER);
    
    assertMatch(2, "Oenanthe", Rank.GENUS);
    assertMatch(3, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(3, "Oenanthe V", Rank.GENUS);
    assertMatch(3, "Oenanthe Vieillot", Rank.GENUS);
    assertMatch(4, "Oenanthe P", Rank.GENUS);
    assertMatch(4, "Oenanthe Pal", Rank.GENUS);
    assertMatch(4, "Œnanthe 1771", Rank.GENUS);
    assertMatch(2, "Œnanthe", Rank.GENUS);
    assertCanonMatch(2, "Oenanthe Camelot", Rank.GENUS);

    assertMatch(6, "Oenanthe aquatica", Rank.SPECIES);
    assertMatch(7, "Oenanthe aquatica Poir", Rank.SPECIES);
    assertMatch(6, "Œnanthe aquatica", Rank.SPECIES);
    
    // it is allowed to add an author to the single current canonical name if it doesnt have an author yet!
    assertMatch(10, "Abies alba", Rank.SPECIES);
    assertMatch(12, "Abies alba Döring, 1778", Rank.SPECIES);
    assertMatch(11, "Abies alba Mumpf.", Rank.SPECIES);
    assertCanonMatch(10,"Abies alba Mill.", Rank.SPECIES);
    assertCanonMatch(10,"Abies alba 1789", Rank.SPECIES);

    // try unparsable names
    assertMatch(16, "Carex cayouettei", Rank.SPECIES);
    assertMatch(17, "Carex comosa × Carex lupulina", Rank.SPECIES);
    assertMatch(24, "Aeropyrum coil-shaped virus", Rank.UNRANKED);
    assertMatch(24, "Aeropyrum coil-shaped virus", Rank.SPECIES);
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/451
   */
  @Test
  public void testSubgenusLookup() throws Exception {
    setupMemory(true);
    List<IndexName> names = List.of(
      //2
      iname("Animalia", Rank.KINGDOM),

      iname("Zyras", Rank.GENUS),
      iname("Zyras", Rank.SUBGENUS),
      //5
      iname("Drusilla", Rank.GENUS),
      //6+7
      iname("Drusilla zyrasoides M.Dvořák, 1988", Rank.SPECIES),
      //8+9
      iname("Myrmedonia (Zyras) alternans Cameron, 1925", Rank.SPECIES),
      //10+11
      iname("Myrmedonia (Zyras) bangae Cameron, 1926", Rank.SPECIES),
      //12+13
      iname("Myrmedonia (Zyras) hirsutiventris Champion, 1927", Rank.SPECIES),
      //14+15
      iname("Zyras (Zyras) alternans (Cameron, 1925)", Rank.SPECIES),
      //16+17
      iname("Zyras bangae (Cameron, 1926)", Rank.SPECIES)
    );
    ni.addAll(names);

    assertEquals(16, ni.size()); // 6 canonical ones
    assertEquals(1, (int) names.get(0).getKey());

    assertMatch(5, "Drusilla zyrasoides", Rank.SPECIES);
    assertMatch(7, "Myrmedonia (Zyras) alternans", Rank.SPECIES);
    assertInsert( "Myrmedonia alternans Cameron, 1925", Rank.SPECIES);
    assertInsert( "Myrmedonia (Larus) alternans Cameron, 1925", Rank.SPECIES);
  
    assertEquals(20, ni.size());
  }

  static Name name(String name, Rank rank) {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(name, rank, null, IssueContainer.VOID).get().getName());
    n.setRank(rank);
    return n;
  }

  static IndexName iname(String name, Rank rank) {
    return new IndexName(name(name, rank));
  }

  private NameMatch assertCanonMatch(Integer key, String name, Rank rank) {
    NameMatch m = assertMatchType(MatchType.CANONICAL, name, rank);
    assertTrue(m.hasMatch());
    assertEquals(key, m.getName().getKey());
    return m;
  }
  
  private NameMatch assertNoMatch(String name, Rank rank) {
    NameMatch m = assertMatchType(MatchType.NONE, name, rank);
    assertFalse(m.hasMatch());
    return m;
  }
  
  private NameMatch assertMatchType(MatchType expected, String name, Rank rank) {
    NameMatch m = match(name, rank);
    if (expected != m.getType()) {
      System.out.println(m);
    }
    assertEquals("No match expected but got " + m.getType(),
        expected, m.getType()
    );
    return m;
  }

  private NameMatch assertMatch(int key, String name, Rank rank) {
    NameMatch m = match(name, rank);
    if (!m.hasMatch() || key != m.getName().getKey()) {
      System.err.println(m);
    }
    assertTrue("Expected single match but got none", m.hasMatch());
    assertEquals("Expected " + key + " but got " + m.getType(), key, (int) m.getName().getKey());
    return m;
  }
  
  private NameMatch assertInsert(String name, Rank rank) {
    NameMatch m = ni.match(name(name, rank), false, false);
    assertEquals(MatchType.NONE, m.getType());
    m = ni.match(name(name, rank), true, false);
    assertEquals(MatchType.EXACT, m.getType());
    return m;
  }
  
  private NameMatch match(String name, Rank rank) {
    NameMatch m = ni.match(name(name, rank), false, true);
    return m;
  }
  
}