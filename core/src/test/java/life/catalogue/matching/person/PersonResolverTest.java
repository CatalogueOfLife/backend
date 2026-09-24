package life.catalogue.matching.person;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class PersonResolverTest {
  static final NomCode ZOO = NomCode.ZOOLOGICAL;

  static Person person(String q, String given, String suffix, Integer born, Integer died, Integer activeFrom, Integer activeTo,
                       Set<TaxGroup> groups) {
    return new Person("wd:" + q, q, null, null, List.of(), "Sowerby", given, suffix, born, died, activeFrom, activeTo, groups,
      PersonSource.WIKIDATA);
  }

  static final Person JAMES1 = person("Q1", "James", null, 1757, 1822, null, null, Set.of());
  static final Person JAMES2 = person("Q2", "James", null, 1815, 1834, null, null, Set.of());
  static final Person GBS2 = person("Q3", "George Brettingham", "II", 1812, 1884, null, null, Set.of(TaxGroup.Molluscs));
  static final Person FLORA = person("Q4", "Flora", null, null, null, 1880, 1890, Set.of());
  static final Person BOTANIST = person("Q5", "Bartholomew", null, null, null, null, null, Set.of(TaxGroup.Angiosperms));

  // explicit margins: the tests must not move when the defaults are set on the corpus
  private final PersonResolver resolver = new PersonResolver(new MemoryPersonStore(new PersonFiles.Content(
    List.of(JAMES1, JAMES2, GBS2, FLORA, BOTANIST),
    List.of(new PersonName("wd:Q1", "James Sowerby", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
      new PersonName("wd:Q2", "James Sowerby", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
      new PersonName("wd:Q3", "G.B. Sowerby II", PersonNameKind.CITATION, PersonFormCode.ZOO, PersonSource.WIKIDATA),
      new PersonName("wd:Q4", "Flora Sowerby", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA),
      new PersonName("wd:Q5", "Bartholomew Sowerby", PersonNameKind.FULL, PersonFormCode.ANY, PersonSource.WIKIDATA)),
    List.of())), new PersonResolver.Margins(10, 20, 15));

  /** "J. Sowerby" of 1821 is the elder James, the younger was six; after 1822 + 20 only the younger is left */
  @Test
  public void yearOfTheName() {
    assertEquals(Set.of(JAMES1), resolver.resolve("J. Sowerby", ZOO, 1821, null));
    assertEquals(Set.of(JAMES2), resolver.resolve("J. Sowerby", ZOO, 1850, null));
    assertEquals(Set.of(), resolver.resolve("J. Sowerby", ZOO, 1860, null));
    assertEquals(Set.of(JAMES1, JAMES2), resolver.resolve("J. Sowerby", ZOO, null, null));
  }

  /** Flora is only known active 1880 to 1890: 15 years either way */
  @Test
  public void activeYearsWithoutLifeDates() {
    assertTrue(resolver.resolve("Sowerby", ZOO, 1870, null).contains(FLORA));
    assertFalse(resolver.resolve("Sowerby", ZOO, 1860, null).contains(FLORA));
    assertFalse(resolver.resolve("Sowerby", ZOO, 1910, null).contains(FLORA));
  }

  /** a botanist is no author of a snail; a person without groups may be anyone's; a broader group is no contradiction */
  @Test
  public void groupOfTheName() {
    Set<Person> snail = resolver.resolve("Sowerby", null, null, TaxGroup.Gastropods);
    assertFalse(snail.contains(BOTANIST));
    assertTrue(snail.contains(JAMES1));
    assertTrue(snail.contains(GBS2));
    assertTrue(resolver.resolve("Sowerby", null, null, TaxGroup.Plants).contains(BOTANIST));
  }

  /** the comparator hands over authors normalized already: they resolve like the citation they came from */
  @Test
  public void normalizedCitationsResolveAlike() {
    for (String c : List.of("G.B. Sowerby II", "J. Sowerby", "Sowerby", "G. B. Sowerby II")) {
      assertEquals(c, resolver.resolve(c, ZOO, null, null), resolver.resolve(AuthorshipNormalizer.normalize(c), ZOO, null, null));
    }
    assertEquals(Set.of(GBS2), resolver.resolve("G.B. Sowerby II", ZOO, null, null));
  }

  /** the year as the parser gives it: an imprecise one narrows nothing */
  @Test
  public void years() {
    assertEquals(Integer.valueOf(1753), PersonResolver.year("1753"));
    assertEquals(Integer.valueOf(1878), PersonResolver.year("1878 [1879]"));
    assertNull(PersonResolver.year("184?"));
    assertNull(PersonResolver.year("12345"));
    assertNull(PersonResolver.year(null));
    assertEquals(Set.of(JAMES1, JAMES2), resolver.resolve("J. Sowerby", ZOO, PersonResolver.year("184?"), null));
  }
}
