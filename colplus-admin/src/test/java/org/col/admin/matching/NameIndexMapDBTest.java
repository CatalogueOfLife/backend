package org.col.admin.matching;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

import com.google.common.collect.Lists;
import org.col.api.model.IssueContainer;
import org.col.api.model.Name;
import org.col.db.mapper.InitMybatisRule;
import org.col.db.mapper.PgSetupRule;
import org.col.parser.NameParser;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NameIndexMapDBTest {
  NameIndex ni;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public InitMybatisRule initMybatisRule = InitMybatisRule.apple();

  @Before
  public void init() throws Exception {
    ni = NameIndexFactory.memory(1, PgSetupRule.getSqlSessionFactory());
  }

  @After
  public void stop() throws Exception {
    ni.close();
  }

  @Test
  public void loadApple() throws SQLException {
    assertEquals(4, ni.size());

    assertEquals(4, (int) matchKey(Rank.SPECIES, "Larus erfundus"));
    assertEquals(4, (int) matchKey(Rank.SPECIES, "Larus erfunda"));
    assertEquals(3, (int) matchKey(Rank.SPECIES, "Larus fusca"));
    assertEquals(2, (int) matchKey(Rank.SPECIES, "Larus fuscus"));
  }

  void addTestData() {
    Collection<Name> names = Lists.newArrayList(
        name(1,  "Animalia", Rank.KINGDOM, NomCode.ZOOLOGICAL),
        name(2,  "Oenanthe Vieillot, 1816", Rank.GENUS, NomCode.ZOOLOGICAL),
        name(3,  "Oenanthe Linnaeus, 1753", Rank.GENUS, NomCode.BOTANICAL),
        name(4,  "Oenanthe aquatica Poir.", Rank.SPECIES, NomCode.BOTANICAL),
        name(5,  "Oenanthe aquatica Senser, 1957", Rank.SPECIES, NomCode.BOTANICAL),
        name(6,  "Oenanthe aquatica", Rank.SPECIES, NomCode.BOTANICAL),
        name(7,  "Rodentia Bowdich, 1821", Rank.ORDER, NomCode.ZOOLOGICAL),
        name(8,  "Rodentia", Rank.GENUS, NomCode.ZOOLOGICAL),
        name(9,  "Abies alba", Rank.SPECIES, NomCode.BOTANICAL),
        name(10, "Abies alba Mumpf.", Rank.SPECIES, NomCode.BOTANICAL),
        name(11, "Abies alba 1778", Rank.SPECIES, NomCode.BOTANICAL),
        name(12, "Picea alba 1778", Rank.SPECIES, NomCode.BOTANICAL),
        name(13, "Picea", Rank.GENUS, NomCode.BOTANICAL),
        name(14, "Carex cayouettei", Rank.SPECIES, NomCode.BOTANICAL),
        name(15, "Carex comosa × Carex lupulina", Rank.SPECIES, NomCode.BOTANICAL),
        name(16, "Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.VIRUS)
    );
    ni.addAll(names);
  }
  
  @Test
  public void testLookup() throws IOException, SQLException {
    addTestData();
    assertEquals(20, ni.size());
    assertEquals(1, (int) matchKey("Animalia", Rank.KINGDOM, NomCode.ZOOLOGICAL));

    assertEquals(7, (int) matchKey("Rodentia", Rank.ORDER, NomCode.ZOOLOGICAL));
    //TODO: filter by nom code !!!
    assertNull(matchKey("Rodentia", Rank.FAMILY, NomCode.ZOOLOGICAL));
    assertNull(matchKey("Rodentia", Rank.ORDER, NomCode.BOTANICAL));
    assertNull(matchKey("Rodenti", Rank.ORDER, NomCode.ZOOLOGICAL));

    assertEquals(7, (int) matchKey("Rodentia Bowdich, 1821", Rank.ORDER, NomCode.ZOOLOGICAL));
    assertEquals(7, (int) matchKey("Rodentia Bowdich, 1221", Rank.ORDER, NomCode.ZOOLOGICAL));
    assertEquals(7, (int) matchKey("Rodentia Bowdich", Rank.ORDER, NomCode.ZOOLOGICAL));
    assertEquals(7, (int) matchKey("Rodentia 1821", Rank.ORDER, NomCode.ZOOLOGICAL));
    assertEquals(7, (int) matchKey("Rodentia Bow.", Rank.ORDER, NomCode.ZOOLOGICAL));
    assertEquals(7, (int) matchKey("Rodentia Bow, 1821", Rank.ORDER, NomCode.ZOOLOGICAL));
    assertEquals(7, (int) matchKey("Rodentia B 1821", Rank.ORDER, NomCode.ZOOLOGICAL));
    assertNull( matchKey("Rodentia Mill., 1823", Rank.ORDER, NomCode.ZOOLOGICAL));

    assertEquals(2, (int) matchKey("Oenanthe", Rank.GENUS, NomCode.ZOOLOGICAL));
    assertEquals(2, (int) matchKey("Oenanthe Vieillot",Rank.GENUS, NomCode.ZOOLOGICAL));
    assertEquals(2, (int) matchKey("Oenanthe V", Rank.GENUS, NomCode.ZOOLOGICAL));
    assertEquals(2, (int) matchKey("Oenanthe Vieillot", Rank.GENUS, null));
    assertEquals(3, (int) matchKey("Oenanthe", Rank.GENUS, NomCode.BOTANICAL));
    assertEquals(3, (int) matchKey("Œnanthe", Rank.GENUS, NomCode.BOTANICAL));
    assertNull(matchKey("Oenanthe", Rank.GENUS, null));
    assertNull(matchKey("Oenanthe Camelot", Rank.GENUS, NomCode.ZOOLOGICAL));

    assertEquals(4, (int) matchKey("Oenanthe aquatica Poir", Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(6, (int) matchKey("Oenanthe aquatica", Rank.SPECIES, NomCode.BOTANICAL));
    assertNull(matchKey("Oenanthe aquatica", Rank.SPECIES, NomCode.BOTANICAL));

    // it is allowed to add an author to the single current canonical name if it doesnt have an author yet!
    assertEquals(9, (int) matchKey("Abies alba", Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(9, (int) matchKey("Abies alba 1789", Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(9, (int) matchKey("Abies alba Mill.",  Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(9, (int) matchKey("Abies alba Miller", Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(9, (int) matchKey("Abies alba Döring, 1778", Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(10, (int) matchKey("Abies alba Mumpf.", Rank.SPECIES, NomCode.BOTANICAL));

    // try unparsable names
    assertEquals(14, (int) matchKey("Carex cayouettei", Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(15, (int) matchKey("Carex comosa × Carex lupulina",Rank.SPECIES, NomCode.BOTANICAL));
    assertEquals(16, (int) matchKey("Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.VIRUS));
    assertEquals(16, (int) matchKey("Aeropyrum coil-shaped virus", Rank.SPECIES, NomCode.VIRUS));
    assertNull(matchKey("Aeropyrum coil-shaped virus", Rank.UNRANKED, NomCode.BOTANICAL));

  }


  static Name name(Integer key, String name, Rank rank, NomCode code) {
    Name n = NameParser.PARSER.parse(name, rank, IssueContainer.VOID).get().getName();
    n.setKey(key);
    n.setRank(rank);
    n.setCode(code);
    return n;
  }

  private Integer matchKey(String name, Rank rank, NomCode code) {
    return (int) ni.match(name(null, name, rank, code), false, true).getName().getKey();
  }

  private Integer matchKey(Rank rank, String name) {
    return matchKey(name, rank, null);
  }
}