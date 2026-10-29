package life.catalogue.basgroup;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameRelationCleanerTest {

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1662
   */
  @Test
  public void isPlausibleSpellingCorrection() throws Exception {
    assertPlausible("Coryphaena hippurus Linnaeus, 1758", "Coriphaena hippurus Linnaeus, 1758", true);
    assertPlausible("Coryphaena scomberoides Valenciennes, 1833", "Coryphaena scomberoïdes Valenciennes, 1833", true);
    assertPlausible("Bembidion pallideguttula Jensen-Haarup, 1910", "Bembidium pallideguttula Jensen-Haarup, 1910", true);
    assertPlausible("Aus smithi Miller", "Aus smithii Miller", true);
    assertPlausible("Trachelosiphon colombianum Schltr.", "Trachelosiphon columbianum Schltr.", true);
    // WoRMS relating an accepted name to the misspelling of one of its synonyms
    assertPlausible("Coryphaena hippurus Linnaeus, 1758", "Coryphaena scomberoïdes Valenciennes, 1833", false);
    assertPlausible("Coryphaena hippurus Linnaeus, 1758", "Coryphaena equiselis Linnaeus, 1758", false);
    assertPlausible("Tilapia sparrmanii Smith, 1840", "Chromis sparrmanni (Smith, 1840)", false);
  }

  /**
   * https://github.com/CatalogueOfLife/data/issues/1815
   */
  @Test
  public void wrongType() throws Exception {
    // the same combination misspelled has no basionym
    assertWrongType("Tilapia sparrmanii Smith, 1840", "Tilapia sparmani Smith, 1840", NomRelType.BASIONYM);
    assertWrongType("Tilapia sparrmanii Smith, 1840", "Tilapia sparrmanni Smith, 1840", NomRelType.BASIONYM);
    assertWrongType("Coryphaena hippurus Linnaeus, 1758", "Coriphaena hippurus Linnaeus, 1758", NomRelType.BASIONYM);
    // another combination is no spelling correction
    assertWrongType("Tilapia sparrmanii Smith, 1840", "Chromis sparrmanni (Smith, 1840)", NomRelType.SPELLING_CORRECTION);
    assertWrongType("Tilapia sparrmanii Smith, 1840", "Chromis sparrmanii (Smith, 1840)", NomRelType.SPELLING_CORRECTION);
    // the names do not tell
    assertWrongType("Tilapia sparrmanii Smith, 1840", "Tilapia sparrmanii (Smith, 1840)", null);
    assertWrongType("Coryphaena hippurus Linnaeus, 1758", "Coriphaena hipurus Linnaeus, 1758", null);
  }

  static Name parse(String name) throws Exception {
    return NameParser.PARSER.parse(name, Rank.SPECIES, null, VerbatimRecord.VOID).get().getName();
  }

  static void assertPlausible(String n1, String n2, boolean expected) throws Exception {
    var name1 = parse(n1);
    var name2 = parse(n2);
    assertEquals(n1 + " vs " + n2, expected, NameRelationCleaner.isPlausibleSpellingCorrection(name1, name2));
    assertEquals(n2 + " vs " + n1, expected, NameRelationCleaner.isPlausibleSpellingCorrection(name2, name1));
  }

  static void assertWrongType(String n1, String n2, NomRelType expected) throws Exception {
    var name1 = parse(n1);
    var name2 = parse(n2);
    assertEquals(n1 + " vs " + n2, expected, NameRelationCleaner.wrongType(name1, name2));
    assertEquals(n2 + " vs " + n1, expected, NameRelationCleaner.wrongType(name2, name1));
  }
}
