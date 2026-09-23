package life.catalogue.matching.authorship;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorMatcher.Mode;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What {@link AuthorComparator} hands to its {@link AuthorMatcher}: the comparator owns years, name structure and team
 * selection, a matcher only ever sees two non empty teams.
 */
public class AuthorComparatorSeamTest {

  record Call(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {}

  static class RecordingMatcher implements AuthorMatcher {
    final List<Call> calls = new ArrayList<>();
    Equality answer = Equality.EQUAL;

    @Override
    public Equality compareTeams(AuthorTeam t1, AuthorTeam t2, AuthorContext ctx, Mode mode) {
      calls.add(new Call(t1, t2, ctx, mode));
      return answer;
    }
  }

  private final RecordingMatcher m = new RecordingMatcher();
  private final AuthorComparator comp = new AuthorComparator(m);

  private static Authorship auth(String year, String... authors) {
    return Authorship.yearAuthors(year, authors);
  }

  @Test
  public void laxHandsOverNormalizedTeamsYearsAndCode() {
    assertEquals(Equality.EQUAL, comp.compare(auth("1753", "L."), auth("1753", "Linné"), NomCode.BOTANICAL));
    assertEquals(List.of(new Call(new AuthorTeam(List.of("l"), "1753"), new AuthorTeam(List.of("linne"), "1753"),
      new AuthorContext(NomCode.BOTANICAL, null), Mode.LAX)), m.calls);
  }

  /** years within the tolerance of 11 but not equal: only close author strings still count */
  @Test
  public void yearConflict() {
    comp.compare(auth("1753", "L."), auth("1758", "Linné"), null);
    assertEquals(Mode.YEAR_CONFLICT, m.calls.get(0).mode());
  }

  /** further apart than the tolerance: the years decide and no author is compared */
  @Test
  public void differentYearsNeedNoMatcher() {
    assertEquals(Equality.DIFFERENT, comp.compare(auth("1753", "L."), auth("1790", "L."), null));
    assertTrue(m.calls.isEmpty());
  }

  /** existing behaviour the person matcher's UNKNOWN meets: in a year conflict, unknown authors are a mismatch */
  @Test
  public void unknownAuthorsInAYearConflictAreDifferent() {
    m.answer = Equality.UNKNOWN;
    assertEquals(Equality.DIFFERENT, comp.compare(auth("1753", "L."), auth("1758", "L."), null));
  }

  /** the lax comparison keeps the ex authors, sources leave them out all the time */
  @Test
  public void laxKeepsExAuthors() {
    Authorship ex = auth(null, "Benth.");
    ex.setExAuthors(new ArrayList<>(List.of("Pohl")));
    comp.compare(ex, auth(null, "Pohl"), NomCode.ZOOLOGICAL);
    assertEquals(List.of("benth", "pohl"), m.calls.get(0).t1().authors());
  }

  /** the strict comparison selects the team by code: the ex author in zoology, the author in botany */
  @Test
  public void strictSelectsTheTeamByCode() {
    Authorship ex = auth(null, "Benth.");
    ex.setExAuthors(new ArrayList<>(List.of("Pohl")));
    comp.compareStrict(ex, auth(null, "Pohl"), NomCode.ZOOLOGICAL, 0);
    comp.compareStrict(ex, auth(null, "Pohl"), NomCode.BOTANICAL, 0);
    assertEquals(new Call(new AuthorTeam(List.of("pohl"), null), new AuthorTeam(List.of("pohl"), null),
      new AuthorContext(NomCode.ZOOLOGICAL, null), Mode.STRICT), m.calls.get(0));
    assertEquals(List.of("benth"), m.calls.get(1).t1().authors());
    assertEquals(Mode.STRICT, m.calls.get(1).mode());
  }

  @Test
  public void neverAnEmptyTeam() {
    assertEquals(Equality.UNKNOWN, comp.compare(auth(null, "L."), new Authorship(), null));
    assertEquals(Equality.UNKNOWN, comp.compare(auth(null, "L."), null, null));
    // the parser turns "et al." into the author "al.", which the normalizer drops
    assertEquals(Equality.UNKNOWN, comp.compare(auth(null, "L."), auth(null, "al."), null));
    assertTrue(m.calls.isEmpty());
  }

  /** a matcher gets the year as given, parsing it is its own business */
  @Test
  public void rawYear() {
    comp.compare(auth("1878 [1879]", "Smith"), auth("1878", "Smith"), null);
    assertEquals("1878 [1879]", m.calls.get(0).t1().year());
  }

  @Test
  public void namesPassCodeAndGroup() {
    Name n1 = new Name();
    n1.setCombinationAuthorship(auth(null, "Sw."));
    Name n2 = new Name();
    n2.setCode(NomCode.ZOOLOGICAL);
    n2.setCombinationAuthorship(auth(null, "Swainson"));
    comp.compare(n1, n2, TaxGroup.Molluscs);
    assertEquals(new AuthorContext(NomCode.ZOOLOGICAL, TaxGroup.Molluscs), m.calls.get(0).ctx());
    // without a group as before
    comp.compare(n1, n2);
    assertEquals(new AuthorContext(NomCode.ZOOLOGICAL, null), m.calls.get(1).ctx());
    // authorships take a group too
    comp.compare(auth(null, "Sw."), auth(null, "Swainson"), NomCode.ZOOLOGICAL, TaxGroup.Molluscs);
    assertEquals(new AuthorContext(NomCode.ZOOLOGICAL, TaxGroup.Molluscs), m.calls.get(2).ctx());
  }
}
