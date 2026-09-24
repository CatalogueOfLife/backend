package life.catalogue.matching.person;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.*;
import life.catalogue.matching.authorship.AuthorMatcher.Mode;
import life.catalogue.matching.person.PersonAuthorMatcher.Explanation;
import life.catalogue.matching.person.PersonAuthorMatcher.RelativesPolicy;
import life.catalogue.matching.person.PersonAuthorMatcher.Rule;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PersonAuthorMatcherTest {

  static Person person(String q, String family, String given, String suffix, Integer born, Integer died, Set<TaxGroup> groups) {
    return new Person("wd:" + q, q, null, null, List.of(), family, given, suffix, born, died, null, null, groups, Provenance.WIKIDATA);
  }

  static final Person WJ_HOOKER = person("Q11", "Hooker", "William Jackson", null, 1785, 1865, Set.of(TaxGroup.Plants));
  static final Person JD_HOOKER = person("Q12", "Hooker", "Joseph Dalton", "f.", 1817, 1911, Set.of(TaxGroup.Plants));
  static final Person C_HOOK = person("Q13", "Hook", "Cathy J.", null, null, null, Set.of(TaxGroup.Molluscs));
  static final Person MILLER = person("Q14", "Miller", "Philip", null, 1691, 1771, Set.of());
  static final Person LINNAEUS = person("Q15", "Linnaeus", "Carl", null, 1707, 1778, Set.of());
  static final Person ARNOTT = person("Q16", "Arnott", "George Arnott Walker", null, 1799, 1868, Set.of());
  static final Person COX = person("Q17", "Cox", "Leslie Reginald", null, 1897, 1965, Set.of());
  static final Person SOWERBY1 = person("Q18", "Sowerby", "George Brettingham", "I", 1788, 1854, Set.of(TaxGroup.Molluscs));
  static final Person SOWERBY2 = person("Q19", "Sowerby", "George Brettingham", "II", 1812, 1884, Set.of(TaxGroup.Molluscs));

  static PersonName name(Person p, String form, NameKind kind, FormCode code) {
    return new PersonName(p.id(), form, kind, code, Provenance.WIKIDATA);
  }

  private final PersonRegistry registry = new PersonRegistry(new PersonFiles.Content(
    List.of(WJ_HOOKER, JD_HOOKER, C_HOOK, MILLER, LINNAEUS, ARNOTT, COX, SOWERBY1, SOWERBY2),
    List.of(name(WJ_HOOKER, "Hook.", NameKind.STANDARD, FormCode.BOT), name(WJ_HOOKER, "William Jackson Hooker", NameKind.FULL, FormCode.ANY),
      name(JD_HOOKER, "Hook.f.", NameKind.STANDARD, FormCode.BOT), name(JD_HOOKER, "Joseph Dalton Hooker", NameKind.FULL, FormCode.ANY),
      name(C_HOOK, "Cathy J. Hook", NameKind.FULL, FormCode.ANY),
      name(MILLER, "Mill.", NameKind.STANDARD, FormCode.BOT), name(LINNAEUS, "L.", NameKind.STANDARD, FormCode.BOT),
      name(ARNOTT, "Arn.", NameKind.STANDARD, FormCode.BOT), name(COX, "Leslie Reginald Cox", NameKind.FULL, FormCode.ANY),
      name(SOWERBY1, "G.B. Sowerby I", NameKind.CITATION, FormCode.ZOO), name(SOWERBY2, "G.B. Sowerby II", NameKind.CITATION, FormCode.ZOO)),
    List.of(new PersonRelation(JD_HOOKER.id(), RelationType.PARENT, WJ_HOOKER.id(), Provenance.WIKIDATA),
      new PersonRelation(SOWERBY2.id(), RelationType.PARENT, SOWERBY1.id(), Provenance.WIKIDATA))));
  private final StringAuthorMatcher strings = new StringAuthorMatcher(AuthorshipNormalizer.INSTANCE);
  private final List<Explanation> seen = new ArrayList<>();

  private PersonAuthorMatcher matcher(RelativesPolicy policy) {
    return new PersonAuthorMatcher(registry, new PersonResolver(registry, new PersonResolver.Margins(10, 20, 15)), strings, policy,
      seen::add);
  }

  /** a team as the comparator hands it over: normalized */
  static AuthorTeam team(@Nullable String year, String... authors) {
    return new AuthorTeam(Arrays.stream(authors).map(AuthorshipNormalizer::normalize).toList(), year);
  }

  static final AuthorContext BOT = new AuthorContext(NomCode.BOTANICAL, null);
  static final AuthorContext ZOO = new AuthorContext(NomCode.ZOOLOGICAL, null);

  @Test
  public void sharedPersonIsEqual() {
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team("1842", "Sowerby"), team("1842", "G.B. Sowerby II"), ZOO, Mode.LAX);
    assertEquals(Equality.EQUAL, e.verdict());
    assertEquals(Rule.IDENTITY, e.decisions().get(0).rule());
  }

  @Test
  public void unrelatedPersonsAreDifferent() {
    assertEquals(Equality.DIFFERENT, matcher(RelativesPolicy.UNKNOWN).compareTeams(team(null, "Mill."), team(null, "L."), BOT, Mode.LAX));
  }

  @Test
  public void relativesFollowThePolicy() {
    assertEquals(Equality.UNKNOWN, matcher(RelativesPolicy.UNKNOWN).compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, Mode.LAX));
    assertEquals(Equality.DIFFERENT, matcher(RelativesPolicy.DIFFERENT).compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, Mode.LAX));
    assertEquals(Rule.RELATIVES, seen.get(0).decisions().get(0).rule());
  }

  /** an unresolved citation meets every form of the persons on the other side: "L. Cox" meets the derived "L. R. Cox" */
  @Test
  public void unresolvedMeetsTheFormsOfTheOtherSide() {
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team(null, "L. Cox"), team(null, "Cox"), BOT, Mode.LAX);
    assertEquals(Equality.EQUAL, e.verdict());
    assertEquals(Rule.FALLBACK, e.decisions().get(0).rule());
    assertEquals(Set.of(), e.decisions().get(0).persons1());
    assertEquals(Set.of(COX), e.decisions().get(0).persons2());
  }

  /** with nobody known on either side the string comparison decides, as it would alone */
  @Test
  public void bothUnknownCompareAsStrings() {
    for (String[] p : List.of(new String[]{"Smith", "Smyth"}, new String[]{"Bory", "Bory de St.-Vincent"}, new String[]{"Rolfe", "Rolfe"})) {
      AuthorTeam t1 = team(null, p[0]);
      AuthorTeam t2 = team(null, p[1]);
      assertEquals(p[0], strings.compareTeams(t1, t2, BOT, Mode.LAX), matcher(RelativesPolicy.UNKNOWN).compareTeams(t1, t2, BOT, Mode.LAX));
    }
  }

  /** the year and the group of the name narrow what a citation may name */
  @Test
  public void narrowsByYearAndGroup() {
    var plants = new AuthorContext(NomCode.BOTANICAL, TaxGroup.Plants);
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team("1850", "Hook."), team("1850", "Hook.f."), plants, Mode.LAX);
    // Cathy J. Hook works on molluscs
    assertEquals(Set.of(WJ_HOOKER), e.decisions().get(0).persons1());
    // Sowerby I died in 1854 and is no author of 1890, Sowerby II is
    Explanation z = matcher(RelativesPolicy.UNKNOWN).explain(team("1890", "Sowerby"), team("1890", "G.B. Sowerby II"), ZOO, Mode.LAX);
    assertEquals(Set.of(SOWERBY2), z.decisions().get(0).persons1());
    assertEquals(Equality.EQUAL, z.verdict());
  }

  /** every candidate ruled out leaves the citation unresolved: the strings decide, never an empty overlap */
  @Test
  public void allRuledOutIsUnresolved() {
    Explanation e = matcher(RelativesPolicy.UNKNOWN).explain(team("1990", "Hook.f."), team("1990", "Hooker f."), BOT, Mode.LAX);
    assertEquals(Rule.FALLBACK, e.decisions().get(0).rule());
    assertEquals(Set.of(), e.decisions().get(0).persons1());
    assertEquals(Equality.EQUAL, e.verdict());
  }

  /** two teams are equal when any author of one is any author of the other, the rule of the string comparison */
  @Test
  public void teamRule() {
    var m = matcher(RelativesPolicy.UNKNOWN);
    assertEquals(Equality.EQUAL, m.compareTeams(team(null, "Hook.", "Arn."), team(null, "Arn."), BOT, Mode.LAX));
    assertEquals(Equality.UNKNOWN, m.compareTeams(team(null, "Hook."), team(null, "Hook.f.", "Mill."), BOT, Mode.LAX));
    assertEquals(Equality.DIFFERENT, m.compareTeams(team(null, "L."), team(null, "Hook.f.", "Mill."), BOT, Mode.LAX));
  }

  /** identity does not depend on the mode, only the fallback does */
  @Test
  public void identityIgnoresTheMode() {
    for (Mode mode : Mode.values()) {
      assertEquals(mode.name(), Equality.UNKNOWN,
        matcher(RelativesPolicy.UNKNOWN).compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, mode));
    }
  }

  @Test
  public void listenerSeesEveryComparison() {
    var m = matcher(RelativesPolicy.UNKNOWN);
    m.compareTeams(team(null, "Mill."), team(null, "L."), BOT, Mode.LAX);
    m.compareTeams(team(null, "Hook."), team(null, "Hook.f."), BOT, Mode.LAX);
    assertEquals(List.of(Equality.DIFFERENT, Equality.UNKNOWN), seen.stream().map(Explanation::verdict).toList());
  }

  /**
   * The comparator combines the author verdict with the years: EQUAL years and an UNKNOWN author pair make EQUAL, and an
   * UNKNOWN in a year conflict is DIFFERENT, rules it had before this matcher. Under the UNKNOWN policy the years decide
   * about relatives, and only without years do they stay UNKNOWN.
   */
  @Test
  public void relativesLetTheYearsDecide() {
    var comparator = new AuthorComparator(matcher(RelativesPolicy.UNKNOWN));
    assertEquals(Equality.EQUAL, comparator.compare(Authorship.yearAuthors("1850", "Hook."), Authorship.yearAuthors("1850", "Hook.f."), NomCode.BOTANICAL));
    assertEquals(Equality.DIFFERENT, comparator.compare(Authorship.yearAuthors("1850", "Hook."), Authorship.yearAuthors("1855", "Hook.f."), NomCode.BOTANICAL));
    assertEquals(Equality.UNKNOWN, comparator.compare(Authorship.authors("Hook."), Authorship.authors("Hook.f."), NomCode.BOTANICAL));
    var strict = new AuthorComparator(matcher(RelativesPolicy.DIFFERENT));
    assertEquals(Equality.DIFFERENT, strict.compare(Authorship.yearAuthors("1850", "Hook."), Authorship.yearAuthors("1850", "Hook.f."), NomCode.BOTANICAL));
  }
}
