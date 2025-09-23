package life.catalogue.assembly;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

public class TreeBaseHandlerUsageTest {

  @Test
  public void usageToString() {
    var u = new TreeHandler.Usage("1234", "p987", Rank.SPECIES, TaxonomicStatus.SYNONYM, null);
    System.out.println(u);
  }
}