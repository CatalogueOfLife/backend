package life.catalogue.matching.decision;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.SimpleName;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class MatchingResultTest {

  @Test
  public void testToString() {
    var mr = new MatchingResult(SimpleName.sn("124", Rank.SPECIES, "Abies", "Miller, 1979"));
    mr.addMatch(TestEntityGenerator.newTaxon("567"));
    mr.addMatch(TestEntityGenerator.newTaxon("568"));
    mr.ignore(TestEntityGenerator.newTaxon("i2"), "forgotten");
    mr.ignore(TestEntityGenerator.newTaxon("iklfw"), "forgotten twin");

    assertEquals("Match for >SPECIES Abies Miller, 1979 [124]< matches: Malus sylvestris Foo [567]; Malus sylvestris Foo [568]. Ignored: Malus sylvestris Foo [i2] - forgotten; Malus sylvestris Foo [iklfw] - forgotten twin", mr.toString());
  }
}