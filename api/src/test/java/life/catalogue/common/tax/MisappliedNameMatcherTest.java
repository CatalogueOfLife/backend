package life.catalogue.common.tax;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;

import org.gbif.nameparser.api.Rank;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MisappliedNameMatcherTest {
  Name n;
  ParsedNameUsage nat;

  @Before
  public void init() {
    n = new Name();
    n.setRank(Rank.SPECIES);
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    nat = new ParsedNameUsage(n, false, null, null);
  }

  @Test
  public void isMisappliedName() {
    nonMisapplied(null);
    nonMisapplied("Markus");
    nonMisapplied("s.str.");
    nonMisapplied("s.l.");
    nonMisapplied("sensu lato");

    misapplied("sensu auct. non Döring 2189");
    misapplied("sensu auctorum");
    misapplied("auct");
    misapplied("auct.");
    misapplied("auct Döring 2189");
    misapplied("auct nec Döring 2189");
    misapplied("nec Döring 2189");
  }

  private void nonMisapplied(String accordingTo) {
    nat.setTaxonomicNote(accordingTo);
    assertFalse(MisappliedNameMatcher.isMisappliedName(nat));
  }

  private void misapplied(String accordingTo) {
    nat.setTaxonomicNote(accordingTo);
    assertTrue(MisappliedNameMatcher.isMisappliedName(nat));
  }
}