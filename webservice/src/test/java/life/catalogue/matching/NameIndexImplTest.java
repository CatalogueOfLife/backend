package life.catalogue.matching;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.parser.NameParser;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class NameIndexImplTest {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.INSTANCE;
  static int keyGen = 1000;

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
      name(1, "Animalia", Rank.KINGDOM, NomCode.ZOOLOGICAL),

      name(2, "Oenanthe Vieillot, 1816", Rank.GENUS, NomCode.ZOOLOGICAL),
      name(3, "Oenanthe Pallas, 1771", Rank.GENUS, NomCode.ZOOLOGICAL),
      name(4, "Oenanthe L.", Rank.GENUS, NomCode.BOTANICAL),

      name(5, "Oenanthe aquatica", Rank.SPECIES, NomCode.BOTANICAL),
      name(6, "Oenanthe aquatica Poir.", Rank.SPECIES, NomCode.BOTANICAL),
      name(7, "Oenanthe aquatica Senser, 1957", Rank.SPECIES, NomCode.BOTANICAL),

      name(8, "Natting tosee", Rank.SPECIES, NomCode.BOTANICAL),

      name(9, "Abies alba", Rank.SPECIES, NomCode.BOTANICAL),
      name(10, "Abies alba Mumpf.", Rank.SPECIES, NomCode.BOTANICAL),
      name(11, "Abies alba 1778", Rank.SPECIES, NomCode.BOTANICAL),

      name(12, "Picea alba 1778", Rank.SPECIES, NomCode.BOTANICAL),
      name(13, "Picea", Rank.GENUS, NomCode.BOTANICAL),
      name(14, "Carex cayouettei", Rank.SPECIES, NomCode.BOTANICAL),
      name(15, "Carex comosa × Carex lupulina", Rank.SPECIES, NomCode.BOTANICAL),

      name(16, "Natting tosee2", Rank.SPECIES, NomCode.BOTANICAL),
      name(17, "Natting tosee3", Rank.SPECIES, NomCode.BOTANICAL),
      name(18, "Natting tosee4", Rank.SPECIES, NomCode.BOTANICAL),
      name(19, "Natting tosee5", Rank.SPECIES, NomCode.BOTANICAL),

      name(20, "Rodentia", Rank.GENUS, NomCode.ZOOLOGICAL),
      name(21, "Rodentia Bowdich, 1821", Rank.ORDER, NomCode.ZOOLOGICAL),

      name(22, "Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.VIRUS)
    ).forEach(n -> {
      ni.add(n);
    });
    assertEquals(22, ni.size());
  }

  void addApples(boolean persist) {
    try (SqlSession s = PgSetupRule.getSqlSessionFactory().openSession()) {
      NameMapper nm = s.getMapper(NameMapper.class);
      NamesIndexMapper nim = s.getMapper(NamesIndexMapper.class);
      // add all apple records to names index
      AtomicInteger counter = new AtomicInteger(1);
      nm.processDataset(TestDataRule.TestData.APPLE.key).forEach(n -> {
        IndexName in = new IndexName(n);
        in.setKey(counter.getAndIncrement());
        if (persist) {
          nim.create(in);
        }
        ni.add(in);
      });
      if (persist) {
        s.commit();
      }
    }
    assertEquals(5, ni.size());
  }

  void setupMemory() throws Exception {
    ni = NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), aNormalizer).started();
  }

  void setupPersistent(File location) throws Exception {
    ni = NameIndexFactory.persistent(location, PgSetupRule.getSqlSessionFactory(), aNormalizer).started();
  }

  @Test
  public void loadApple() throws Exception {
    setupMemory();
    addApples(false);

    assertMatch(5, "Larus erfundus", Rank.SPECIES, null);
    assertMatch(5, "Larus erfunda", Rank.SPECIES, null);
    assertMatch(4, "Larus fusca", Rank.SPECIES, null);
    assertMatch(3, "Larus fuscus", Rank.SPECIES, null);
  }

  /**
   * Try to add the same name again and multiple names with the same key
   */
  @Test
  public void add() throws Exception {
    setupMemory();
    ni.add(create("Abies", "krösus-4-par∂atœs"));
    ni.add(create("Abies", "alba"));

    IndexName n = create("Abies", "alba", "1873");
    ni.add(n);

    n = create("Abies", "alba", "1873");
    final int key = n.getKey();
    ni.add(n);
    assertEquals(4, ni.size());

    n = create("Abies", "alba", "1873", "Miller");
    n.setKey(key);
    ni.add(n);
    assertEquals(4, ni.size());

    n = create("Abies", "alba", "1873", "Mill.");
    n.setKey(key);
    ni.add(n);
    assertEquals(4, ni.size());
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

    IndexName in = new IndexName(n);
    in.setKey(keyGen++);
    return in;
  }

  @Test
  public void restart() throws Exception {
    File fIdx = File.createTempFile("col", ".nidx");
    if (fIdx.exists()) {
      fIdx.delete();
    }

    try {
      setupPersistent(fIdx);
      addApples(true);

      assertMatch(5, "Larus erfundus", Rank.SPECIES, null);

      System.out.println("RESTART");
      assertEquals(5, ni.size());
      ni.stop();
      ni.start();
      assertEquals(5, ni.size());
      assertMatch(5, "Larus erfundus", Rank.SPECIES, null);

      ni.stop();
      try {
        match("Larus erfundus", Rank.SPECIES, null);
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
    setupMemory();
    assertInsert("Larus fundatus", Rank.SPECIES, null);
    assertInsert("Puma concolor", Rank.SPECIES, NomCode.ZOOLOGICAL);
  }
  
  @Test
  public void testLookup() throws Exception {
    setupMemory();
    addTestNames();

    assertMatch(3, "Œnanthe 1771", Rank.GENUS, null);
    
    assertEquals(22, ni.size());
    assertMatch(1, "Animalia", Rank.KINGDOM, NomCode.ZOOLOGICAL);
    
    assertMatch(21, "Rodentia", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertNoMatch("Rodentia", Rank.ORDER, NomCode.BOTANICAL);
    assertNoMatch("Rodenti", Rank.ORDER, NomCode.ZOOLOGICAL);
    
    assertMatch(21, "Rodentia Bowdich, 1821", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertMatch(21, "Rodentia Bowdich, 1221", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertMatch(21, "Rodentia Bowdich", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertMatch(21, "Rodentia 1821", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertMatch(21, "Rodentia Bow.", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertMatch(21, "Rodentia Bow, 1821", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertMatch(21, "Rodentia B 1821", Rank.ORDER, NomCode.ZOOLOGICAL);
    assertMatch(21, "Rodentia", Rank.FAMILY, NomCode.ZOOLOGICAL);
    assertNoMatch("Rodentia Mill., 1823", Rank.SUBORDER, NomCode.ZOOLOGICAL);
    
    assertMatch(4, "Oenanthe", Rank.GENUS, NomCode.BOTANICAL);
    assertMatch(2, "Oenanthe Vieillot", Rank.GENUS, NomCode.ZOOLOGICAL);
    assertMatch(2, "Oenanthe V", Rank.GENUS, NomCode.ZOOLOGICAL);
    assertMatch(2, "Oenanthe Vieillot", Rank.GENUS, null);
    assertNoMatch("Oenanthe P", Rank.GENUS, NomCode.BOTANICAL);
    assertMatch(3, "Oenanthe Pal", Rank.GENUS, null);
    assertMatch(3, "Œnanthe 1771", Rank.GENUS, null);
    assertMatch(4, "Œnanthe", Rank.GENUS, NomCode.BOTANICAL);
    assertAmbiguousMatch("Oenanthe", Rank.GENUS, null, 2, 3, 4);
    assertAmbiguousMatch("Oenanthe", Rank.GENUS, NomCode.ZOOLOGICAL, 2, 3);
    assertNoMatch("Oenanthe Camelot", Rank.GENUS, NomCode.ZOOLOGICAL);
    
    assertMatch(5, "Oenanthe aquatica", Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(6, "Oenanthe aquatica Poir", Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(5, "Œnanthe aquatica", Rank.SPECIES, NomCode.BOTANICAL);
    
    // it is allowed to add an author to the single current canonical name if it doesnt have an author yet!
    assertMatch(9, "Abies alba", Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(11, "Abies alba Döring, 1778", Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(10, "Abies alba Mumpf.", Rank.SPECIES, NomCode.BOTANICAL);
    assertAmbiguousMatch("Abies alba Mill.", Rank.SPECIES, NomCode.BOTANICAL, 9,11);
    assertAmbiguousMatch("Abies alba Miller", Rank.SPECIES, NomCode.BOTANICAL, 9,11);
    assertAmbiguousMatch("Abies alba 1789", Rank.SPECIES, NomCode.BOTANICAL, 9,10);
    
    // try unparsable names
    assertMatch(14, "Carex cayouettei", Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(15, "Carex comosa × Carex lupulina", Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(22, "Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.VIRUS);
    assertMatch(22, "Aeropyrum coil-shaped virus", Rank.SPECIES, NomCode.VIRUS);
    assertNoMatch("Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.BOTANICAL);
    
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/451
   */
  @Test
  public void testSubgenusLookup() throws Exception {
    setupMemory();
    Collection<IndexName> names = Lists.newArrayList(
        name(1, "Animalia", Rank.KINGDOM, NomCode.ZOOLOGICAL),
        
        name(2, "Zyras", Rank.GENUS, NomCode.ZOOLOGICAL),
        name(3, "Zyras", Rank.SUBGENUS, NomCode.ZOOLOGICAL),
        name(4, "Drusilla", Rank.GENUS, NomCode.ZOOLOGICAL),
        
        name(5, "Drusilla zyrasoides M.Dvořák, 1988", Rank.SPECIES, NomCode.ZOOLOGICAL),
        name(6, "Myrmedonia (Zyras) alternans Cameron, 1925", Rank.SPECIES, NomCode.ZOOLOGICAL),
        name(7, "Myrmedonia (Zyras) bangae Cameron, 1926", Rank.SPECIES, NomCode.ZOOLOGICAL),
        name(8, "Myrmedonia (Zyras) hirsutiventris Champion, 1927", Rank.SPECIES, NomCode.ZOOLOGICAL),
        
        name(9, "Zyras (Zyras) alternans (Cameron, 1925)", Rank.SPECIES, NomCode.ZOOLOGICAL),
        name(10, "Zyras bangae (Cameron, 1926)", Rank.SPECIES, NomCode.ZOOLOGICAL)
    );
    ni.addAll(names);
  
    assertEquals(10, ni.size());
  
    assertMatch(5, "Drusilla zyrasoides", Rank.SPECIES, null);
    assertMatch(6, "Myrmedonia (Zyras) alternans", Rank.SPECIES, NomCode.ZOOLOGICAL);
    assertInsert( "Myrmedonia alternans Cameron, 1925", Rank.SPECIES, NomCode.ZOOLOGICAL);
    assertInsert( "Myrmedonia (Larus) alternans Cameron, 1925", Rank.SPECIES, NomCode.ZOOLOGICAL);
  
    assertEquals(12, ni.size());
  }

  static Name name(String name, Rank rank, NomCode code) {
    Name n = TestEntityGenerator.setUserDate(NameParser.PARSER.parse(name, rank, code, IssueContainer.VOID).get().getName());
    n.setRank(rank);
    n.setCode(code);
    return n;
  }

  static IndexName name(Integer key, String name, Rank rank, NomCode code) {
    IndexName n = new IndexName(name(name, rank, code));
    n.setKey(key);
    return n;
  }
  
  private NameMatch assertAmbiguousMatch(String name, Rank rank, NomCode code, Integer... keys) {
    NameMatch m = assertMatchType(MatchType.AMBIGUOUS, name, rank, code);
    assertTrue(m.hasMatch());
    Set<Integer> expected = Sets.newHashSet(keys);
    for (Integer k : m.getNameIds()) {
      assertTrue("Missing match " + k, expected.remove(k));
    }
    assertTrue(expected.isEmpty());
    return m;
  }
  
  private NameMatch assertNoMatch(String name, Rank rank, NomCode code) {
    NameMatch m = assertMatchType(MatchType.NONE, name, rank, code);
    assertFalse(m.hasMatch());
    return m;
  }
  
  private NameMatch assertMatchType(MatchType expected, String name, Rank rank, NomCode code) {
    NameMatch m = match(name, rank, code);
    if (expected != m.getType()) {
      System.out.println(m);
    }
    assertEquals("No match expected but got " + m.getType(),
        expected, m.getType()
    );
    return m;
  }

  private NameMatch assertMatch(int key, String name, Rank rank, NomCode code) {
    NameMatch m = match(name, rank, code);
    if (!m.hasMatch() || m.getNames().size() != 1 || key != m.getNames().get(0).getKey()) {
      System.out.println(m);
    }
    assertTrue("Expected single match but got " + m.getNames().size(), m.hasSingleMatch());
    assertEquals("Expected " + key + " but got " + m.getType(), key, (int) m.getNames().get(0).getKey());
    return m;
  }
  
  private NameMatch assertInsert(String name, Rank rank, NomCode code) {
    NameMatch m = ni.match(name(name, rank, code), true, false);
    assertEquals(MatchType.INSERTED, m.getType());
    return m;
  }
  
  private NameMatch match(String name, Rank rank, NomCode code) {
    NameMatch m = ni.match(name(name, rank, code), false, true);
    return m;
  }
  
}