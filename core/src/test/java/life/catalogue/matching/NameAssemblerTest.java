package life.catalogue.matching;

import life.catalogue.api.model.Classification;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The test cases of matching-ws' NameNRankTest (https://github.com/gbif/matching-ws, Apache License 2.0).
 */
public class NameAssemblerTest {

  @Test
  public void build() {
    assertName("Asteraceae Mill.", "Asteraceae", "Mill.", null, null, null, null);
    assertName("Lepidothrix iris L.", "Lepidothrix iris", "L.", "iris", "", null, null);

    Classification cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setPhylum("Chordata");
    cl.setClass_("Aves");
    cl.setOrder("Passeriformes");
    cl.setFamily("Pipridae");
    cl.setGenus("Lepidothrix");

    assertName("Lepidothrix iris L.", "", "L.", null, "iris", "", cl);
    assertName("Lepidothrix iris L.", "L. iris", "L.", null, "", "null", cl);
    assertName("Lepidothrix iris L.", "L. iris", "L.", "Lepidothrix", "", null, null);
    assertName("L. iris", "L. iris", "L.", null, "", null, null);
    assertName("L. iris Miller", "L. iris", "Miller", null, "", null, null);

    cl.setSpecies("iris");
    assertName("Lepidothrix iris L.", "iris", "L.", null, "", null, cl);
    cl.setSpecies("Lepidothrix iris");
    assertName("Lepidothrix iris L.", null, "L.", null, "", null, cl);

    // without classification
    assertName("Lepidothrix iris L.", null, "L.", "Lepidothrix", "iris", null, cl);
    // a whole binomial as the epithet
    assertName("Lepidothrix iris L.", null, "L.", "Lepidothrix", "Lepidothrix iris", null, cl);
    var r = NameAssembler.build(null, "L.", "Lepidothrix", "iris", "alpina", Rank.VARIETY, cl);
    assertEquals("Lepidothrix iris var. alpina L.", r.name());
    assertEquals(Rank.VARIETY, r.rank());

    assertGoodName("Aa calceata (Rchb.f.) Schltr.", null);
    assertGoodName("Aa calceata (Rchb.f.) Schltr.", "(Rchb.f.) Schltr.");
    assertGoodName("Aa calceata (Rchb.f.) Schltr.", "Schltr.");
    assertGoodName("Deltalipothrixvirus SBFV3", null);
    assertGoodName("SH002390.07FU", null);
    assertGoodName("Acidianus filamentous virus 7", null);
    assertGoodName("Lepidothrix iris L.", null);
    assertGoodName("Polygola vulgaris var. alpina DC.", null);
    assertGoodName("Polygola vulgaris var. alpina DC.", "DC.");
  }

  /**
   * Not in matching-ws, which builds "? iris L." here: without any genus epithets cannot be matched,
   * and nothing else was given.
   */
  @Test
  public void epithetsWithoutGenus() {
    var cl = new Classification();
    cl.setFamily("Pipridae");
    assertNull(NameAssembler.build(null, "L.", null, "iris", null, null, cl).name());

    // the lowest taxon of the classification if nothing else is given
    var r = NameAssembler.build(null, null, null, null, null, null, cl);
    assertEquals("Pipridae", r.name());
    assertEquals(Rank.FAMILY, r.rank());
    assertTrue(r.fromClassification());
  }

  static void assertName(String expected, String name, String authorship, String genericName, String specificEpithet,
                         String infraspecificEpithet, Classification cl) {
    var r = NameAssembler.build(name, authorship, genericName, specificEpithet, infraspecificEpithet, null, cl);
    assertEquals(expected, r.name());
    assertNull(r.rank());
  }

  static void assertGoodName(String name, String authorship) {
    assertName(name, name, authorship, null, null, null, null);
  }

  @Test
  public void appendAuthorship() {
    assertEquals("Abies Mill.", NameAssembler.appendAuthorship("Abies", "Mill."));
    assertEquals("Abies Mill.", NameAssembler.appendAuthorship("Abies ", "Mill. "));
    assertEquals("Abies Mill.", NameAssembler.appendAuthorship("Abies Mill.", "Mill."));
    assertNull(NameAssembler.appendAuthorship(null, "Mill."));
    assertNull(NameAssembler.appendAuthorship(" ", "Mill."));
  }

  @Test
  public void expandAbbreviatedGenus() {
    assertEquals("Abies Mill.", NameAssembler.expandAbbreviatedGenus("Abies Mill.", ""));
    assertEquals("Abies alba Mill.", NameAssembler.expandAbbreviatedGenus("Abies alba Mill.", "Abies"));
    assertEquals("Abies alba Mill.", NameAssembler.expandAbbreviatedGenus("A alba Mill.", "Abies"));
    assertEquals("Abies alba Mill.", NameAssembler.expandAbbreviatedGenus("A. alba Mill.", "Abies"));
    assertEquals("Abies alba Mill.", NameAssembler.expandAbbreviatedGenus("? alba Mill.", "Abies"));
    assertEquals("xecrtvfzgbhnjml,mtre", NameAssembler.expandAbbreviatedGenus("xecrtvfzgbhnjml,mtre", "Abies"));
    assertEquals("FU234567", NameAssembler.expandAbbreviatedGenus("FU234567", "Abies"));
  }

  @Test
  public void isSimpleBinomial() {
    assertTrue(NameAssembler.isSimpleBinomial("Abies alba"));
    assertTrue(NameAssembler.isSimpleBinomial("Abies  alba "));
    assertTrue(NameAssembler.isSimpleBinomial("Adalia bipunctata"));
    assertTrue(NameAssembler.isSimpleBinomial("Adalia bi-punctata"));
    assertTrue(NameAssembler.isSimpleBinomial("Adalia 8-punctata"));
    assertTrue(NameAssembler.isSimpleBinomial("Adalia 8punctata"));

    assertFalse(NameAssembler.isSimpleBinomial("Abies alba Mill."));
    assertFalse(NameAssembler.isSimpleBinomial("alba Mill."));
    assertFalse(NameAssembler.isSimpleBinomial("alba"));
    assertFalse(NameAssembler.isSimpleBinomial("Abies"));
  }
}
