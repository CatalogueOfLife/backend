package life.catalogue.resources;

import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TreeResource2Test {
  
  @Test
  public void parse() {
    assertEquals(Rank.ORDER, TreeResource.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--ORDER").rank);
    assertEquals(Rank.SPECIES, TreeResource.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--SPECIES").rank);
    assertEquals("55812e8-5422-402e-b071-b67a9cdf481f", TreeResource.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--ORDER").getId());
    assertNull(TreeResource.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f").rank);
  }
  
}