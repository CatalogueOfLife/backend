package life.catalogue.api.util;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.DwcTerm;

import org.junit.Test;

import static org.junit.Assert.*;

public class RankUtilsTest {

  @Test
  public void coldpRanks() {
    for (var term : ColdpTerm.DENORMALIZED_RANKS) {
      var rank = RankUtils.RANK2COLDP.inverse().get(term);
      assertNotNull("missing term " + term, rank);
    }
  }

  @Test
  public void dwcRanks() {
    for (var term : RankUtils.RANK2DWC.values()) {
      assertEquals(DwcTerm.GROUP_TAXON, term.getGroup());
    }
  }

}