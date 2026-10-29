package life.catalogue.basgroup;

import life.catalogue.api.model.ConsolidationName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.matching.similarity.ModifiedDamerauLevenshtein;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HomotypicRulesTest {

  @Test
  public void isSameName() throws Exception {
    compare("Abies", "Abies", true);
    compare("Abies alba", "Abies alba", true);
    compare("Abies alba", "Abies alpa", true);
    compare("Abies alba", "Apia alba", false);
    compare("Mesolecanium nigrofasciatum", "Mesolecanium nigrofaciatum", true);
    compare("Mesolecanium nigrofasciatum", "Mesolecanium nicrofaciatum", false);
  }

  @Test
  public void isOrthographicVariant() throws Exception {
    variant("Trachelosiphon colombianum Schltr.", "Trachelosiphon columbianum Schltr.", Rank.SPECIES, true);
    variant("Bdellodes iranensis Ueckermann", "Bdellodes iraniensis Ueckermann", Rank.SPECIES, true);
    // gender endings
    variant("Aus albus L.", "Aus alba L.", Rank.SPECIES, true);
    variant("Aus bus var. cus Miller", "Aus bus var. kus Miller", Rank.VARIETY, true);
    // the same spelling is a duplicate, not a variant
    variant("Aus albus L.", "Aus albus Linnaeus", Rank.SPECIES, false);
    variant("Machimus erevanensis Richter, 1963", "Machimus (Machimus) erevanensis Richter, 1963", Rank.SPECIES, false);
    // a genus misspelled by a single letter, https://github.com/CatalogueOfLife/backend/issues/1662
    variant("Coryphaena hippurus Linnaeus, 1758", "Coriphaena hippurus Linnaeus, 1758", Rank.SPECIES, true);
    variant("Bisciris meridionalis Thor", "Biscirus meridionalis Thor", Rank.SPECIES, true);
    // another genus, or one misspelled by more than a letter
    variant("Bembidion pallideguttula Jensen-Haarup, 1910", "Bembidium pallideguttula Jensen-Haarup, 1910", Rank.SPECIES, false);
    variant("Octomeria colombiana Schltr.", "Aa colombiana Schltr.", Rank.SPECIES, false);
    variant("Corypaena hippurua Linnaeus, 1758", "Coryphaena hippurus Linnaeus, 1758", Rank.SPECIES, false);
    // another species
    variant("Aus bus var. cus Miller", "Aus dus var. kus Miller", Rank.VARIETY, false);
    // the subgenus is not part of the name
    variant("Cyclops (Acanthocyclops) stammeri Kiefer", "Cyclops (Megacyclops) stammerii Kiefer", Rank.SPECIES, true);
    // another rank
    assertFalse(HomotypicRules.isOrthographicVariant(parse("Negundo violaceum G.Kirchn.", Rank.SPECIES),
      parse("Negundo aceroides var. violacea G.Kirchn.", Rank.VARIETY)));
  }

  @Test
  public void isDuplicate() throws Exception {
    duplicate("Aus albus L.", "Aus albus Linnaeus", Rank.SPECIES, true);
    // the subgenus is not part of a species name, https://github.com/CatalogueOfLife/backend/issues/1662
    duplicate("Machimus erevanensis Richter, 1963", "Machimus (Machimus) erevanensis Richter, 1963", Rank.SPECIES, true);
    duplicate("Ampelita (Poecilostylus) julii Fischer-Piette, 1965", "Ampelita (Eurystyla) julii Fischer-Piette, 1965", Rank.SPECIES, true);
    duplicate("Aus albus L.", "Aus alba L.", Rank.SPECIES, false);
    duplicate("Coryphaena hippurus L.", "Coriphaena hippurus L.", Rank.SPECIES, false);
    // another rank
    assertFalse(HomotypicRules.isDuplicate(parse("Drimeotus similis Bokor", Rank.SPECIES),
      parse("Drimeotus similis Bokor", Rank.SUBSPECIES)));
  }

  void duplicate(String n1, String n2, Rank rank, boolean expected) throws Exception {
    assertEquals(n1 + " vs " + n2, expected, HomotypicRules.isDuplicate(parse(n1, rank), parse(n2, rank)));
    assertEquals(n2 + " vs " + n1, expected, HomotypicRules.isDuplicate(parse(n2, rank), parse(n1, rank)));
  }

  void variant(String n1, String n2, Rank rank, boolean expected) throws Exception {
    assertEquals(n1 + " vs " + n2, expected, HomotypicRules.isOrthographicVariant(parse(n1, rank), parse(n2, rank)));
    assertEquals(n2 + " vs " + n1, expected, HomotypicRules.isOrthographicVariant(parse(n2, rank), parse(n1, rank)));
  }

  static Name parse(String name, Rank rank) throws Exception {
    return NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName();
  }

  void compare(String n1, String n2, boolean same) {
    var cn1 = new ConsolidationName();
    cn1.setName(n1);
    var cn2 = new ConsolidationName();
    cn2.setName(n2);
    assertEquals(same, HomotypicRules.isSameName(cn1, cn2, new ModifiedDamerauLevenshtein()));
  }
}
