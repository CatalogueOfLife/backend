package life.catalogue.resources;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.MemoryPersonStore;
import life.catalogue.matching.person.PersonFiles;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PersonResourceTest {
  static final Person SWARTZ = new Person("wd:Q3", "Q3", "10103-1", null, List.of("ipni:10103-2"), "Swartz", "Olof", null,
    1760, 1818, null, null, Set.of(TaxGroup.Angiosperms), PersonSource.WIKIDATA);
  static final Person CANDOLLE = new Person("wd:Q312", "Q312", null, null, List.of(), "Candolle", "Augustin Pyramus de", null,
    1778, 1841, null, null, Set.of(), PersonSource.WIKIDATA);

  private final PersonResource resource = new PersonResource(new MemoryPersonStore(new PersonFiles.Content(
    List.of(SWARTZ, CANDOLLE),
    List.of(new PersonName("wd:Q3", "Sw.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI),
      new PersonName("wd:Q312", "DC.", PersonNameKind.STANDARD, PersonFormCode.BOT, PersonSource.IPNI)),
    List.of())), null);

  /** any id the person answers to, and a 404 for one nobody does */
  @Test
  public void anyId() {
    for (String id : List.of("wd:Q3", "ipni:10103-1", "ipni:10103-2")) {
      assertEquals(id, SWARTZ, resource.get(id).person());
    }
    assertThrows(NotFoundException.class, () -> resource.get("wd:Q404"));
  }

  @Test
  public void statuses() {
    assertEquals(PersonMatch.Status.RESOLVED, resource.match("Sw.", "BOTANICAL", null, null).status());
    assertEquals(PersonMatch.Status.UNKNOWN, resource.match("Sw.", "ZOOLOGICAL", null, null).status());
    assertEquals(PersonMatch.Status.RULED_OUT, resource.match("Sw.", null, "1900", null).status());
    assertEquals(PersonMatch.Status.RULED_OUT, resource.match("Sw.", null, null, "Molluscs").status());
  }

  @Test
  public void authorship() {
    var m = resource.matchAuthorship("Sw. ex DC.", "BOTANICAL", null);
    assertEquals(List.of(CANDOLLE), m.combination().get(0).candidates());
    assertEquals(List.of(SWARTZ), m.combinationEx().get(0).candidates());
  }

  /** parameters as the rest of the API takes them, and a 400 - an IllegalArgumentException - for anything else */
  @Test
  public void parameters() {
    assertEquals(PersonMatch.Status.RESOLVED, resource.match("Sw.", "botanical", "1800", "angiosperms").status());
    assertThrows(IllegalArgumentException.class, () -> resource.match("Sw.", "martian", null, null));
    assertThrows(IllegalArgumentException.class, () -> resource.match("Sw.", null, null, "Dragons"));
    assertThrows(IllegalArgumentException.class, () -> resource.match("Sw.", null, "18x", null));
    assertThrows(IllegalArgumentException.class, () -> resource.match(" ", null, null, null));
    assertThrows(IllegalArgumentException.class, () -> resource.matchAuthorship("Sw.", "martian", null));
  }
}
