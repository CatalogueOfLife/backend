package life.catalogue.matching.person;

import life.catalogue.api.event.PersonsChanged;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.PersonConfig;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static life.catalogue.api.vocab.PersonFormCode.*;
import static life.catalogue.api.vocab.PersonNameKind.*;
import static life.catalogue.api.vocab.PersonRelationType.PARENT;
import static life.catalogue.api.vocab.PersonSource.*;
import static life.catalogue.matching.person.PersonTablesTest.*;
import static org.junit.Assert.*;

public class PgPersonStoreTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();

  static final Person SWARTZ = new Person("wd:Q3", "Q3", "10103-1", null, List.of(), "Swartz", "Olof", null, 1760, 1818, null,
    null, Set.of(TaxGroup.Angiosperms), WIKIDATA);

  static PersonFiles.Content content(Person swartz) {
    return new PersonFiles.Content(List.of(SOWERBY1, SOWERBY2, swartz, DOE),
      List.of(new PersonName("wd:Q1", "G.B.Sowerby I", CITATION, ZOO, WIKIDATA),
        new PersonName("wd:Q2", "G.B.Sowerby II", CITATION, ZOO, WIKIDATA),
        new PersonName("wd:Q3", "Sw.", STANDARD, BOT, IPNI),
        new PersonName("wd:Q3", "Olof Swartz", FULL, ANY, WIKIDATA),
        new PersonName("clb:1", "Ann Doe", FULL, ANY, CURATED)),
      List.of(new PersonRelation("wd:Q2", PARENT, "wd:Q1", WIKIDATA)));
  }

  private PgPersonStore store;

  @Before
  public void init() throws Exception {
    PersonTables.replace(factory(), content(SWARTZ));
    store = new PgPersonStore(factory(), new PersonConfig());
  }

  @Test
  public void byKeyAndCode() {
    assertEquals(Set.of(SWARTZ), store.byKey("sw", NomCode.BOTANICAL));
    assertEquals(Set.of(SWARTZ), store.byKey("sw", null));
    assertEquals(Set.of(), store.byKey("sw", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(SOWERBY1, SOWERBY2), store.candidates("Sowerby", NomCode.ZOOLOGICAL));
    assertEquals(Set.of(SOWERBY2), store.candidates("G. B. Sowerby II", NomCode.BOTANICAL));
  }

  @Test
  public void byKeys() {
    assertEquals(Map.of("sw", Set.of(SWARTZ), "sowerby", Set.of(SOWERBY1, SOWERBY2)),
      store.byKeys(List.of("sw", "sowerby", "nobody"), NomCode.BOTANICAL));
    // no key, no select
    assertEquals(Map.of(), store.byKeys(List.of(), null));
    assertEquals(Set.of(), store.candidates(".", null));
  }

  /** any id finds the person, and a person reached by two ids is still one candidate */
  @Test
  public void anyId() {
    assertEquals(SOWERBY2, store.get("wd:Q2"));
    assertEquals(SOWERBY2, store.get("ipni:9936-1"));
    assertNull(store.get("wd:Q404"));
    assertEquals(1, store.candidates("G.B.Sowerby II", NomCode.ZOOLOGICAL).size());
  }

  @Test
  public void relativesAndKeys() {
    assertEquals(Set.of(SOWERBY1), store.relatives(SOWERBY2));
    assertEquals(Set.of(SOWERBY2), store.relatives(SOWERBY1));
    assertEquals(Set.of(), store.relatives(SWARTZ));
    assertEquals(Set.of("sw", "olof swartz", "o swartz", "swartz"), store.keys(SWARTZ, NomCode.BOTANICAL));
    assertEquals(Set.of("olof swartz", "o swartz", "swartz"), store.keys(SWARTZ, NomCode.ZOOLOGICAL));
  }

  @Test
  public void retiredIsFoundByIdAndCuratedForms() {
    assertEquals(DOE, store.get("clb:1"));
    assertEquals(Set.of(DOE), store.candidates("Ann Doe", null));
    assertEquals(Set.of(), store.candidates("Doe", null));
  }

  @Test
  public void infoWithoutDerivedForms() {
    var info = store.info("ipni:9936-1");
    assertEquals(SOWERBY2, info.person());
    assertEquals(List.of(new PersonName("wd:Q2", "G.B.Sowerby II", CITATION, ZOO, WIKIDATA)), info.names());
    assertEquals(List.of(new PersonRelation("wd:Q2", PARENT, "wd:Q1", WIKIDATA)), info.relations());
    assertNull(store.info("wd:Q404"));
  }

  /** the caches answer until the registry announces a change, whatever the tables hold meanwhile */
  @Test
  public void cachesClearedOnPersonsChanged() throws Exception {
    assertEquals(Set.of(SWARTZ), store.byKey("sw", NomCode.BOTANICAL));
    var olaf = new Person("wd:Q3", "Q3", "10103-1", null, List.of(), "Swartz", "Olaf", null, 1760, 1818, null, null,
      Set.of(TaxGroup.Angiosperms), WIKIDATA);
    PersonTables.replace(factory(), content(olaf));
    assertEquals(Set.of(SWARTZ), store.byKey("sw", NomCode.BOTANICAL));
    store.personsChanged(new PersonsChanged(Users.TESTER));
    assertEquals(Set.of(olaf), store.byKey("sw", NomCode.BOTANICAL));
  }
}
