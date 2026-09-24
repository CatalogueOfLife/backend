package life.catalogue.matching.person;

import life.catalogue.api.model.AuthorshipPersonMatch;
import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonMatch;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;

import org.gbif.nameparser.api.NomCode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import static life.catalogue.api.model.PersonMatch.Status.*;
import static org.junit.Assert.*;

public class PersonMatchServiceTest {
  static final NomCode BOT = NomCode.BOTANICAL;
  static final NomCode ZOO = NomCode.ZOOLOGICAL;

  static Person person(String q, String family, String given, Integer born, Integer died, Set<TaxGroup> groups) {
    return new Person("wd:" + q, q, null, null, List.of(), family, given, null, born, died, null, null, groups,
      PersonSource.WIKIDATA);
  }

  static PersonName name(String q, String form, PersonNameKind kind, PersonFormCode code) {
    return new PersonName("wd:" + q, form, kind, code, PersonSource.WIKIDATA);
  }

  static final Person LINNAEUS = person("Q1043", "Linnaeus", "Carl", 1707, 1778, Set.of(TaxGroup.Plants));
  static final Person MILLER = person("Q380", "Miller", "Philip", 1691, 1771, Set.of());
  static final Person CANDOLLE = person("Q312", "Candolle", "Augustin Pyramus de", 1778, 1841, Set.of());
  static final Person FRIES = person("Q364", "Fries", "Elias Magnus", 1794, 1878, Set.of(TaxGroup.Fungi));
  static final Person JAMES1 = person("Q1", "Sowerby", "James", 1757, 1822, Set.of());
  static final Person JAMES2 = person("Q2", "Sowerby", "James", 1815, 1834, Set.of());

  static final MemoryPersonStore STORE = new MemoryPersonStore(new PersonFiles.Content(
    List.of(LINNAEUS, MILLER, CANDOLLE, FRIES, JAMES1, JAMES2),
    List.of(name("Q1043", "L.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q380", "Mill.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q312", "DC.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q364", "Fr.", PersonNameKind.STANDARD, PersonFormCode.BOT),
      name("Q1", "James Sowerby", PersonNameKind.FULL, PersonFormCode.ANY),
      name("Q2", "James Sowerby", PersonNameKind.FULL, PersonFormCode.ANY)),
    List.of()));

  // explicit margins: the tests must not move when the defaults do
  private final PersonMatchService service = new PersonMatchService(STORE, new PersonResolver.Margins(10, 20, 15));

  @Test
  public void resolved() {
    var m = service.match("L.", BOT, null, null);
    assertEquals(RESOLVED, m.status());
    assertEquals("L.", m.citation());
    assertEquals(PersonKeys.key("L."), m.key());
    assertEquals(List.of(LINNAEUS), m.candidates());
  }

  @Test
  public void ambiguous() {
    var m = service.match("J. Sowerby", ZOO, null, null);
    assertEquals(AMBIGUOUS, m.status());
    assertEquals(Set.of(JAMES1, JAMES2), Set.copyOf(m.candidates()));
  }

  /** nobody has the form, and a botanical standard form cites nobody in zoology */
  @Test
  public void unknown() {
    assertEquals(UNKNOWN, service.match("Nobody", BOT, null, null).status());
    var m = service.match("L.", ZOO, null, null);
    assertEquals(UNKNOWN, m.status());
    assertEquals(List.of(), m.candidates());
  }

  @Test
  public void punctuationOnly() {
    var m = service.match(".", null, null, null);
    assertEquals(UNKNOWN, m.status());
    assertNull(m.key());
  }

  /** both Jameses were dead for more than 20 years by 1900, and a mycologist named no mollusc */
  @Test
  public void ruledOut() {
    var m = service.match("J. Sowerby", ZOO, 1900, null);
    assertEquals(RULED_OUT, m.status());
    assertEquals(Set.of(JAMES1, JAMES2), Set.copyOf(m.candidates()));
    m = service.match("Fr.", BOT, null, TaxGroup.Molluscs);
    assertEquals(RULED_OUT, m.status());
    assertEquals(List.of(FRIES), m.candidates());
    // a year leaving one person resolves to it
    m = service.match("J. Sowerby", ZOO, 1850, null);
    assertEquals(RESOLVED, m.status());
    assertEquals(List.of(JAMES2), m.candidates());
  }

  @Test
  public void authorshipSlots() {
    var m = service.matchAuthorship("(L.) Mill. ex DC.", BOT, null);
    assertEquals(AuthorshipPersonMatch.Status.PARSED, m.status());
    assertEquals(List.of(CANDOLLE), one(m.combination()).candidates());
    assertEquals(List.of(MILLER), one(m.combinationEx()).candidates());
    assertEquals(List.of(LINNAEUS), one(m.basionym()).candidates());
    assertEquals(List.of(), m.basionymEx());
    assertEquals(List.of(), m.sanctioning());

    var s = service.matchAuthorship("L. : Fr.", BOT, null);
    assertEquals(List.of(LINNAEUS), one(s.combination()).candidates());
    assertEquals(List.of(FRIES), one(s.sanctioning()).candidates());
  }

  /** every slot is narrowed by its own year: the basionym of 1820 is the elder James, the combination of 1850 the younger */
  @Test
  public void slotsKeepTheirYears() {
    var m = service.matchAuthorship("(J. Sowerby, 1820) J. Sowerby, 1850", ZOO, null);
    assertEquals(List.of(JAMES1), one(m.basionym()).candidates());
    assertEquals(List.of(JAMES2), one(m.combination()).candidates());
  }

  @Test
  public void unparsable() {
    var unreadable = new PersonMatchService(STORE, PersonResolver.Margins.DEFAULT, (a, c) -> Optional.empty());
    var m = unreadable.matchAuthorship("L. & ???", BOT, null);
    assertEquals(AuthorshipPersonMatch.Status.UNPARSABLE, m.status());
    assertEquals(List.of(), m.combination());
    assertEquals(List.of(), m.sanctioning());
  }

  private static PersonMatch one(List<PersonMatch> matches) {
    assertEquals(matches.toString(), 1, matches.size());
    return matches.get(0);
  }
}
