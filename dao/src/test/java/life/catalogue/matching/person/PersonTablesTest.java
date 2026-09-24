package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.api.vocab.PersonFormCode.*;
import static life.catalogue.api.vocab.PersonNameKind.*;
import static life.catalogue.api.vocab.PersonRelationType.PARENT;
import static life.catalogue.api.vocab.PersonSource.*;
import static org.junit.Assert.*;

public class PersonTablesTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  static final Person SOWERBY1 = new Person("wd:Q1", "Q1", null, null, List.of(), "Sowerby", "George Brettingham", "I",
    1788, 1854, null, null, Set.of(), WIKIDATA);
  static final Person SOWERBY2 = new Person("wd:Q2", "Q2", "9936-1", null, List.of("ipni:9936-1"), "Sowerby",
    "George Brettingham", "II", 1812, 1884, null, null, Set.of(TaxGroup.Molluscs, TaxGroup.Angiosperms), WIKIDATA);
  static final Person DOE = new Person("clb:1", null, null, null, List.of(), "Doe", "Ann", null, null, null, null, null,
    Set.of(), CURATED, LocalDate.of(2026, 9, 24), null);

  static PersonFiles.Content content() {
    return new PersonFiles.Content(List.of(SOWERBY1, SOWERBY2, DOE),
      List.of(new PersonName("wd:Q1", "G.B.Sowerby I", CITATION, ZOO, WIKIDATA),
        // by a former id, written under the person's own
        new PersonName("ipni:9936-1", "G.B.Sowerby II", CITATION, ZOO, CURATED),
        new PersonName("clb:1", "Ann Doe", FULL, ANY, CURATED)),
      List.of(new PersonRelation("ipni:9936-1", PARENT, "wd:Q1", WIKIDATA)));
  }

  static SqlSessionFactory factory() {
    return SqlSessionFactoryRule.getSqlSessionFactory();
  }

  static PersonFiles.Content read() {
    try (SqlSession session = factory().openSession(true)) {
      return PersonTables.read(session);
    }
  }

  static List<String> column(String sql) throws SQLException {
    try (SqlSession session = factory().openSession(true);
         Statement st = session.getConnection().createStatement();
         ResultSet rs = st.executeQuery(sql)) {
      List<String> values = new ArrayList<>();
      while (rs.next()) {
        values.add(rs.getString(1));
      }
      return values;
    }
  }

  /** references by any id come back by the person's own id, no derived form comes back */
  @Test
  public void roundTrip() throws Exception {
    PersonTables.replace(factory(), content());
    var c = read();
    assertEquals(Set.of(SOWERBY1, SOWERBY2, DOE), Set.copyOf(c.persons()));
    assertEquals(Set.of(new PersonName("wd:Q1", "G.B.Sowerby I", CITATION, ZOO, WIKIDATA),
        new PersonName("wd:Q2", "G.B.Sowerby II", CITATION, ZOO, CURATED),
        new PersonName("clb:1", "Ann Doe", FULL, ANY, CURATED)),
      Set.copyOf(c.names()));
    assertEquals(List.of(new PersonRelation("wd:Q2", PARENT, "wd:Q1", WIKIDATA)), c.relations());
  }

  @Test
  public void derivedFormsAndIds() throws Exception {
    PersonTables.replace(factory(), content());
    assertEquals(List.of("G. B. Sowerby II", "Sowerby", "Sowerby II"),
      column("SELECT form FROM person_name WHERE person_id = 'wd:Q2' AND kind = 'DERIVED' ORDER BY form"));
    assertEquals(List.of(PersonKeys.key("G.B.Sowerby II")), column("SELECT key FROM person_name WHERE form = 'G.B.Sowerby II'"));
    assertEquals(List.of("ipni:9936-1", "wd:Q2"), column("SELECT any_id FROM person_id WHERE person_id = 'wd:Q2' ORDER BY any_id"));
    // a retired person derives nothing
    assertEquals(List.of(), column("SELECT form FROM person_name WHERE person_id = 'clb:1' AND kind = 'DERIVED'"));
  }

  /** derived forms are computed anew on every write: a changed given name changes the initials */
  @Test
  public void derivedFormsFollowThePerson() throws Exception {
    PersonTables.replace(factory(), content());
    var renamed = new Person("wd:Q2", "Q2", "9936-1", null, List.of("ipni:9936-1"), "Sowerby", "James", "II", 1812, 1884,
      null, null, Set.of(TaxGroup.Molluscs, TaxGroup.Angiosperms), WIKIDATA);
    PersonTables.replace(factory(), new PersonFiles.Content(List.of(SOWERBY1, renamed, DOE), content().names(),
      content().relations()));
    assertEquals(List.of("J. Sowerby II", "Sowerby", "Sowerby II"),
      column("SELECT form FROM person_name WHERE person_id = 'wd:Q2' AND kind = 'DERIVED' ORDER BY form"));
  }

  /** the export reads the registry in one read only snapshot, all three tables of one moment */
  @Test
  public void snapshot() throws Exception {
    PersonTables.replace(factory(), content());
    var c = PersonTables.read(factory());
    assertEquals(read().persons(), c.persons());
    assertEquals(read().names(), c.names());
    assertEquals(read().relations(), c.relations());
  }

  /** reads come sorted, so two harvests of the same sources merge the registry in the same order */
  @Test
  public void readsAreSorted() throws Exception {
    PersonTables.replace(factory(), content());
    var c = read();
    assertEquals(List.of("clb:1", "wd:Q1", "wd:Q2"), c.persons().stream().map(Person::id).toList());
    assertEquals(List.of("clb:1", "wd:Q1", "wd:Q2"), c.names().stream().map(PersonName::person).toList());
  }

  /** a rewrite keeps when a person was created, and when it was modified unless it changed */
  @Test
  public void timestamps() throws Exception {
    PersonTables.replace(factory(), content());
    var first = stamps();
    PersonTables.replace(factory(), content());
    assertEquals(first, stamps());

    var changed = new ArrayList<>(content().persons());
    changed.set(0, new Person("wd:Q1", "Q1", null, null, List.of(), "Sowerby", "George Brettingham", "I", 1788, 1855, null,
      null, Set.of(), WIKIDATA));
    PersonTables.replace(factory(), new PersonFiles.Content(changed, content().names(), content().relations()));
    var after = stamps();
    assertEquals(first.get("wd:Q1").get(0), after.get("wd:Q1").get(0));
    assertNotEquals(first.get("wd:Q1").get(1), after.get("wd:Q1").get(1));
    assertEquals(first.get("wd:Q2"), after.get("wd:Q2"));
  }

  private static Map<String, List<String>> stamps() throws SQLException {
    Map<String, List<String>> stamps = new HashMap<>();
    for (String row : column("SELECT id || '|' || created || '|' || modified FROM person")) {
      String[] parts = row.split("\\|");
      stamps.put(parts[0], List.of(parts[1], parts[2]));
    }
    return stamps;
  }

  /** an inconsistent registry is refused whole, the tables keep what they held */
  @Test
  public void inconsistentIsRefused() throws Exception {
    PersonTables.replace(factory(), content());
    var bad = new PersonFiles.Content(List.of(SOWERBY1),
      List.of(new PersonName("wd:Q404", "Nobody", FULL, ANY, CURATED)), List.of());
    var e = assertThrows(IllegalArgumentException.class, () -> PersonTables.replace(factory(), bad));
    assertTrue(e.getMessage(), e.getMessage().contains("unknown person wd:Q404"));
    assertEquals(3, read().persons().size());
  }

  /**
   * An Error halfway through the write, heap exhaustion above all, leaves the registry as it was. The names are read once
   * by the check and fail the second time, when the writer has deleted every row and copied the persons already.
   */
  @Test
  public void anErrorWritesNothing() throws Exception {
    PersonTables.replace(factory(), content());
    List<PersonName> names = content().names();
    List<PersonName> failing = new AbstractList<>() {
      int reads;

      @Override
      public PersonName get(int i) {
        return names.get(i);
      }

      @Override
      public int size() {
        return names.size();
      }

      @Override
      public Iterator<PersonName> iterator() {
        if (++reads > 1) {
          throw new OutOfMemoryError("test");
        }
        return names.iterator();
      }
    };
    var c = new PersonFiles.Content(content().persons(), failing, content().relations());
    assertThrows(OutOfMemoryError.class, () -> PersonTables.replace(factory(), c));
    assertEquals(Set.of(SOWERBY1, SOWERBY2, DOE), Set.copyOf(read().persons()));
    assertEquals(3, read().names().size());
    assertEquals(1, read().relations().size());
  }

  /** quotes, commas, apostrophes and backslashes survive the copy and its array literals */
  @Test
  public void quoting() throws Exception {
    var odd = new Person("wd:Q5", "Q5", null, null, List.of("clb:\"5\\"), "O'Brien, Jr", "Ann \"Nan\"", null, null, null,
      null, null, Set.of(TaxGroup.Plants), CURATED);
    var c = new PersonFiles.Content(List.of(odd),
      List.of(new PersonName("wd:Q5", "d'Orb., \"x\" \\ y", VARIANT, ANY, CURATED)), List.of());
    PersonTables.replace(factory(), c);
    var read = read();
    assertEquals(List.of(odd), read.persons());
    assertEquals(c.names(), read.names());
  }
}
