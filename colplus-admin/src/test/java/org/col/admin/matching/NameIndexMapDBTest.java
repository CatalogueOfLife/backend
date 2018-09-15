package org.col.admin.matching;

import java.util.Collection;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.col.api.model.IssueContainer;
import org.col.api.model.Name;
import org.col.api.model.NameMatch;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.MatchType;
import org.col.db.mapper.InitMybatisRule;
import org.col.db.PgSetupRule;
import org.col.parser.NameParser;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameIndexMapDBTest {
  NameIndex ni;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.apple();

  @After
  public void stop() throws Exception {
    if (ni != null) {
      ni.close();
    }
  }

  void setupApple() throws Exception {
    ni = NameIndexFactory.memory(11, PgSetupRule.getSqlSessionFactory());
  }

  void setupTest() throws Exception {
    ni = NameIndexFactory.memory(Datasets.PROV_CAT, PgSetupRule.getSqlSessionFactory());
    Collection<Name> names = Lists.newArrayList(
        name(1,  "Animalia", Rank.KINGDOM, NomCode.ZOOLOGICAL),

        name(2,  "Oenanthe Vieillot, 1816", Rank.GENUS, NomCode.ZOOLOGICAL),
        name(3,  "Oenanthe Pallas, 1771", Rank.GENUS, NomCode.ZOOLOGICAL),
        name(4,  "Oenanthe L.", Rank.GENUS, NomCode.BOTANICAL),

        name(5,  "Oenanthe aquatica", Rank.SPECIES, NomCode.BOTANICAL),
        name(6,  "Oenanthe aquatica Poir.", Rank.SPECIES, NomCode.BOTANICAL),
        name(7,  "Oenanthe aquatica Senser, 1957", Rank.SPECIES, NomCode.BOTANICAL),

        name(8,  "Natting tosee", Rank.SPECIES, NomCode.BOTANICAL),

        name(9,  "Abies alba", Rank.SPECIES, NomCode.BOTANICAL),
        name(10, "Abies alba Mumpf.", Rank.SPECIES, NomCode.BOTANICAL),
        name(11, "Abies alba 1778", Rank.SPECIES, NomCode.BOTANICAL),

        name(12, "Picea alba 1778", Rank.SPECIES, NomCode.BOTANICAL),
        name(13, "Picea", Rank.GENUS, NomCode.BOTANICAL),
        name(14, "Carex cayouettei", Rank.SPECIES, NomCode.BOTANICAL),
        name(15, "Carex comosa × Carex lupulina", Rank.SPECIES, NomCode.BOTANICAL),

        name(16,  "Natting tosee2", Rank.SPECIES, NomCode.BOTANICAL),
        name(17,  "Natting tosee3", Rank.SPECIES, NomCode.BOTANICAL),
        name(18,  "Natting tosee4", Rank.SPECIES, NomCode.BOTANICAL),
        name(19,  "Natting tosee5", Rank.SPECIES, NomCode.BOTANICAL),

        name(20,  "Rodentia", Rank.GENUS, NomCode.ZOOLOGICAL),
        name(21,  "Rodentia Bowdich, 1821", Rank.ORDER, NomCode.ZOOLOGICAL),

        name(22, "Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.VIRUS)
    );
    ni.addAll(names);
  }

  @Test
  public void loadApple() throws Exception {
    setupApple();
    assertEquals(4, ni.size());

    assertMatch(4, "Larus erfundus", Rank.SPECIES, null);
    assertMatch(4, "Larus erfunda", Rank.SPECIES, null);
    assertMatch(3, "Larus fusca", Rank.SPECIES, null);
    assertMatch(2, "Larus fuscus", Rank.SPECIES, null);
  }

  @Test
  public void testHasids() throws Exception {
    assertEquals("l5w5nl",  NameIndexMapDB.HASHIDS.encode(1000));
    assertEquals("l5r34.",  NameIndexMapDB.HASHIDS.encode(10000));
    assertEquals("l54--r",  NameIndexMapDB.HASHIDS.encode(100000));
    assertEquals("5wb-9m",  NameIndexMapDB.HASHIDS.encode(1000000));
    assertEquals("575-qx",  NameIndexMapDB.HASHIDS.encode(10000000));
    assertEquals("5w4-q$n", NameIndexMapDB.HASHIDS.encode(20000000));
    assertEquals("5qaej9m",  NameIndexMapDB.HASHIDS.encode(50000000));
    assertEquals("9q5+9_ry", NameIndexMapDB.HASHIDS.encode(Integer.MAX_VALUE));
  }

  @Test
  public void insertNewNames() throws Exception {
    setupApple();
    assertInsert("Larus fundatus", Rank.SPECIES, null);
    assertInsert("Puma concolor", Rank.SPECIES, NomCode.ZOOLOGICAL);
  }

  @Test
  public void testLookup() throws Exception {
    setupTest();

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
    assertMatch(2, "Oenanthe Vieillot",Rank.GENUS, NomCode.ZOOLOGICAL);
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
    assertMatch(10,"Abies alba Mumpf.", Rank.SPECIES, NomCode.BOTANICAL);
    assertAmbiguousMatch("Abies alba Mill.",  Rank.SPECIES, NomCode.BOTANICAL);
    assertAmbiguousMatch("Abies alba Miller", Rank.SPECIES, NomCode.BOTANICAL);
    assertAmbiguousMatch("Abies alba 1789", Rank.SPECIES, NomCode.BOTANICAL);

    // try unparsable names
    assertMatch(14, "Carex cayouettei", Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(15, "Carex comosa × Carex lupulina",Rank.SPECIES, NomCode.BOTANICAL);
    assertMatch(22, "Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.VIRUS);
    assertMatch(22, "Aeropyrum coil-shaped virus", Rank.SPECIES, NomCode.VIRUS);
    assertNoMatch("Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.BOTANICAL);

  }


  static Name name(Integer key, String name, Rank rank, NomCode code) {
    Name n = NameParser.PARSER.parse(name, rank, IssueContainer.VOID).get().getName();
    if (key != null) {
      n.setId(NameIndexMapDB.HASHIDS.encode(key));
    }
    n.setRank(rank);
    n.setCode(code);
    return n;
  }

  private NameMatch assertAmbiguousMatch(String name, Rank rank, NomCode code, Integer... keys) {
    NameMatch m = assertMatchType(MatchType.AMBIGUOUS, name, rank, code);
    assertFalse(m.hasMatch());
    Set<Integer> expected = Sets.newHashSet(keys);
    for (Integer k : keys) {
      assertTrue("Missing alt key " + k, expected.remove(k));
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
    assertEquals("No match expected but got "+m.getType(),
        expected, m.getType()
    );
    return m;
  }
  
  private NameMatch assertMatch(int key, String name, Rank rank, NomCode code) {
    final String id = NameIndexMapDB.HASHIDS.encode(key);
    NameMatch m = match(name, rank, code);
    if (!m.hasMatch() || !id.equals(m.getName().getId())) {
      System.out.println(m);
    }
    assertEquals("Expected "+id+" but got "+m.getType(), id, m.getName().getId()
    );
    return m;
  }


  private NameMatch assertInsert(String name, Rank rank, NomCode code) {
    NameMatch m = ni.match(name(null, name, rank, code), true, false);
    assertEquals(MatchType.INSERTED, m.getType());
    return m;
  }

  private NameMatch match(String name, Rank rank, NomCode code) {
    NameMatch m = ni.match(name(null, name, rank, code), false, true);
    return m;
  }
}