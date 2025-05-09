package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RankIDTest {

  @Test
  public void parse() {
    assertEquals(Rank.ORDER, RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--ORDER").rank);
    assertEquals(Rank.SPECIES, RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--INCERTAE-SEDIS--SPECIES").rank);
    assertEquals("55812e8-5422-402e-b071-b67a9cdf481f", RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--ORDER").getId());
    assertNull(RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f").rank);
    assertEquals(Rank.SPECIES, RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--incertae-sedis--species").rank);
    assertEquals(Rank.SPECIES, RankID.parseID(3,"55812e8-5422-402e-b071-b67a9cdf481f--Incertae-Sedis--Species").rank);

    assertEquals("", RankID.parseID(3,"").getId());
    assertEquals(null, RankID.parseID(3,null).getId());

    assertNull(RankID.parseID(null));
  }
}