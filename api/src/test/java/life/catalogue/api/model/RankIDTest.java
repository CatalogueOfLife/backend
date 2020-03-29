package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.*;

public class RankIDTest {

  @Test
  public void parse() {
    assertEquals(Rank.ORDER, RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--ORDER").rank);
    assertEquals(Rank.SPECIES, RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--SPECIES").rank);
    assertEquals("55812e8-5422-402e-b071-b67a9cdf481f", RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--ORDER").getId());
    assertNull(RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f").rank);
  }
}