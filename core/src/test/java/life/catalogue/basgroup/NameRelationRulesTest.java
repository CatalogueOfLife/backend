package life.catalogue.basgroup;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.basgroup.NameRelationRules.Rel;
import life.catalogue.basgroup.NameRelationRules.Verdict;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static life.catalogue.api.vocab.NomRelType.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Cases taken from COL26.10 XR 316691: https://github.com/CatalogueOfLife/data/issues/1815
 */
public class NameRelationRulesTest {

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

  @Test
  public void isPlausibleBasionym() throws Exception {
    assertTrue(NameRelationRules.isPlausibleBasionym(sp("Chromis sparrmanii (Smith, 1840)"), sp("Tilapia sparrmanii Smith, 1840")));
    assertFalse(NameRelationRules.isPlausibleBasionym(sp("Tilapia sparrmanii Smith, 1840"), sp("Chromis sparrmanii (Smith, 1840)")));
    // a misspelling has no basionym
    assertFalse(NameRelationRules.isPlausibleBasionym(sp("Tilapia sparmani Smith, 1840"), sp("Tilapia sparrmanii Smith, 1840")));
    // the rank may change within its group, ICN Art. 6.10
    assertTrue(NameRelationRules.isPlausibleBasionym(sp("Baptisia texana (Holz.) Pollard & C.R.Ball"),
      name("b", "Baptisia lanceolata var. texana Holz.", Rank.VARIETY)));
    assertTrue(NameRelationRules.isPlausibleBasionym(name("a", "Aus bus cus (Smith, 1900)", Rank.SUBSPECIES), sp("Aus cus Smith, 1900")));
    assertTrue(NameRelationRules.isPlausibleBasionym(genus("Crupina (Pers.) DC."), name("c", "Centaurea subg. Crupina Pers.", Rank.SUBGENUS)));
    // but not across groups: a species is never the basionym of a genus
    assertFalse(NameRelationRules.isPlausibleBasionym(genus("Crupina (Pers.) DC."), sp("Centaurea crupina L.")));
    assertFalse(NameRelationRules.isPlausibleBasionym(genus("Hydradendrium Carter, 1880"), sp("Hydradendrium spinosum Carter, 1880")));
  }

  @Test
  public void isPlausibleType() throws Exception {
    assertTrue(NameRelationRules.isPlausibleType(genus("Hydradendrium Carter, 1880"), sp("Hydradendrium spinosum Carter, 1880")));
    assertTrue(NameRelationRules.isPlausibleType(name("f", "Cichlidae", Rank.FAMILY), genus("Tilapia Smith, 1840")));
    assertFalse(NameRelationRules.isPlausibleType(sp("Hydradendrium spinosum Carter, 1880"), genus("Hydradendrium Carter, 1880")));
    assertFalse(NameRelationRules.isPlausibleType(genus("Typhaeus"), genus("Typhoeus Beddard, 1883")));
    assertFalse(NameRelationRules.isPlausibleType(genus("Aus Smith, 1900"), name("b", "Aus subgen. Bus Smith, 1900", Rank.SUBGENUS)));
  }

  @Test
  public void basionymAndSpellingCorrection() throws Exception {
    // WoRMS: a misspelling is no recombination
    var spar = sp("Tilapia sparrmanii Smith, 1840");
    var sparmani = sp("Tilapia sparmani Smith, 1840");
    assertVerdicts(spar, sparmani, List.of(rel(SPELLING_CORRECTION, spar, sparmani), rel(BASIONYM, sparmani, spar)),
      "remove BASIONYM Tilapia sparmani>Tilapia sparrmanii NAME_RELATION_CONFLICT");
    // a misspelled recombination: the spelling correction is implausible already
    var chromis = sp("Chromis sparrmanni (Smith, 1840)");
    assertVerdicts(spar, chromis, List.of(rel(SPELLING_CORRECTION, spar, chromis), rel(BASIONYM, chromis, spar)),
      "remove SPELLING_CORRECTION Tilapia sparrmanii>Chromis sparrmanni SPELLING_CORRECTION_IMPLAUSIBLE");
    // a recombination with brackets keeps its basionym
    var hipurus = sp("Coryphaena hipurus (Linnaeus, 1758)");
    var hippurus = sp("Coryphaena hippurus Linnaeus, 1758");
    assertVerdicts(hipurus, hippurus, List.of(rel(SPELLING_CORRECTION, hippurus, hipurus), rel(BASIONYM, hipurus, hippurus)),
      "remove SPELLING_CORRECTION Coryphaena hippurus>Coryphaena hipurus NAME_RELATION_CONFLICT");
    // a genus spelled differently by the same author is no recombination either
    var lupa = genus("Lupa Leach, 1814");
    var lupea = genus("Lupea Leach, 1814");
    assertVerdicts(lupa, lupea, List.of(rel(SPELLING_CORRECTION, lupa, lupea), rel(BASIONYM, lupea, lupa)),
      "remove BASIONYM Lupea>Lupa NAME_RELATION_CONFLICT");
    var thysano = genus("Thysanoserolis Brandt, 1991");
    var thysanero = genus("Thysaneroserolis Brandt, 1991");
    assertVerdicts(thysano, thysanero, List.of(rel(SPELLING_CORRECTION, thysano, thysanero), rel(BASIONYM, thysanero, thysano)),
      "remove BASIONYM Thysaneroserolis>Thysanoserolis NAME_RELATION_CONFLICT");
  }

  @Test
  public void basionymAndOtherHomotypicTypes() throws Exception {
    var texana = sp("Baptisia texana (Holz.) Pollard & C.R.Ball");
    var var = name("var", "Baptisia lanceolata var. texana Holz.", Rank.VARIETY);
    assertVerdicts(texana, var, List.of(rel(BASIONYM, texana, var), rel(REPLACEMENT_NAME, var, texana)),
      "remove REPLACEMENT_NAME var>Baptisia texana NAME_RELATION_CONFLICT");
    var alucita = sp("Alucita xanthonzona (Diakonoff, 1954)");
    var orneodes = sp("Orneodes xanthozona Diakonoff, 1954");
    assertVerdicts(alucita, orneodes, List.of(rel(BASIONYM, alucita, orneodes), rel(SUPERFLUOUS, alucita, orneodes)),
      "remove SUPERFLUOUS Alucita xanthonzona>Orneodes xanthozona NAME_RELATION_CONFLICT");
  }

  @Test
  public void laterHomonym() throws Exception {
    var later = genus("Eupholus Guérin-Méneville, 1838");
    var senior = genus("Eupholus Boisduval, 1835");
    // the ids are the labels, so both names need distinct ones
    later.setId("Eupholus later");
    assertVerdicts(later, senior, List.of(rel(HOMOTYPIC, later, senior), rel(LATER_HOMONYM, later, senior)),
      "remove HOMOTYPIC Eupholus later>Eupholus NAME_RELATION_CONFLICT");
  }

  @Test
  public void basionymAndType() throws Exception {
    // a genus typified by a species is no basionym of it
    var genus = genus("Hydradendrium Carter, 1880");
    var species = sp("Hydradendrium spinosum Carter, 1880");
    assertVerdicts(genus, species, List.of(rel(BASIONYM, genus, species), rel(TYPE, genus, species)),
      "remove BASIONYM Hydradendrium>Hydradendrium spinosum NAME_RELATION_CONFLICT");
    // a species typifies no species
    var comb = sp("Aus bus (L.)");
    var orig = sp("Xus bus L.");
    assertVerdicts(comb, orig, List.of(rel(BASIONYM, comb, orig), rel(TYPE, comb, orig)),
      "remove TYPE Aus bus>Xus bus NAME_RELATION_CONFLICT");
    // two genera, neither fits: both stay
    var typhaeus = genus("Typhaeus");
    var typhoeus = genus("Typhoeus Beddard, 1883");
    assertVerdicts(typhaeus, typhoeus, List.of(rel(BASIONYM, typhaeus, typhoeus), rel(TYPE, typhaeus, typhoeus)),
      "flag BASIONYM Typhaeus>Typhoeus NAME_RELATION_CONFLICT",
      "flag TYPE Typhaeus>Typhoeus NAME_RELATION_CONFLICT");
  }

  @Test
  public void bothDirections() throws Exception {
    // the nomenclatural status tells
    var tridens = genus("Tridens Roem. & Schult.");
    var tricuspis = genus("Tricuspis P.Beauv.");
    tricuspis.setNomStatus(NomStatus.UNACCEPTABLE);
    assertVerdicts(tridens, tricuspis, List.of(rel(REPLACEMENT_NAME, tridens, tricuspis), rel(REPLACEMENT_NAME, tricuspis, tridens)),
      "remove REPLACEMENT_NAME Tricuspis>Tridens NAME_RELATION_CONFLICT");
    // the year tells
    var smeathman = sp("Termes destructor Smeathman, 1781");
    var degeer = sp("Termes destructor De Geer, 1778");
    degeer.setId("Termes destructor senior");
    assertVerdicts(smeathman, degeer, List.of(rel(LATER_HOMONYM, smeathman, degeer), rel(LATER_HOMONYM, degeer, smeathman)),
      "remove LATER_HOMONYM Termes destructor senior>Termes destructor NAME_RELATION_CONFLICT");
    // nothing tells
    var albata = sp("Carex albata Boott ex Franch. & Sav.");
    var argyrolepis = sp("Carex argyrolepis Maxim. ex Peter");
    assertVerdicts(albata, argyrolepis, List.of(rel(REPLACEMENT_NAME, albata, argyrolepis), rel(REPLACEMENT_NAME, argyrolepis, albata)),
      "flag REPLACEMENT_NAME Carex albata>Carex argyrolepis NAME_RELATION_CONFLICT",
      "flag REPLACEMENT_NAME Carex argyrolepis>Carex albata NAME_RELATION_CONFLICT");
  }

  @Test
  public void redundantHomotypic() throws Exception {
    var neuquenina = genus("Neuquenina Rosas Costa, 1959");
    var neuquenia = genus("Neuquenia Rehn, 1943");
    assertVerdicts(neuquenina, neuquenia, List.of(rel(REPLACEMENT_NAME, neuquenina, neuquenia), rel(HOMOTYPIC, neuquenia, neuquenina)),
      "remove HOMOTYPIC Neuquenia>Neuquenina NAME_RELATION_REDUNDANT");
    var embolyntha = genus("Embolyntha Davis, 1940");
    var olyntha = genus("Olyntha Gray, 1832");
    assertVerdicts(embolyntha, olyntha, List.of(rel(HOMOTYPIC, embolyntha, olyntha), rel(HOMOTYPIC, olyntha, embolyntha)),
      "remove HOMOTYPIC Olyntha>Embolyntha NAME_RELATION_REDUNDANT");
    // a single relation is fine
    assertVerdicts(embolyntha, olyntha, List.of(rel(HOMOTYPIC, embolyntha, olyntha)));
  }

  static Name name(String id, String name, Rank rank) throws Exception {
    var n = NameParser.PARSER.parse(name, rank, null, VerbatimRecord.VOID).get().getName();
    n.setId(id);
    return n;
  }

  /**
   * @return a species name with its canonical name as the id
   */
  static Name sp(String name) throws Exception {
    var n = name(null, name, Rank.SPECIES);
    n.setId(n.getScientificName());
    return n;
  }

  static Name genus(String name) throws Exception {
    var n = name(null, name, Rank.GENUS);
    n.setId(n.getScientificName());
    return n;
  }

  static Rel rel(NomRelType type, Name from, Name to) {
    return new Rel(type, from.getId(), to.getId());
  }

  static void assertVerdicts(Name n1, Name n2, List<Rel> rels, String... expected) {
    var verdicts = NameRelationRules.check(n1, n2, rels);
    assertEquals(Set.of(expected), verdicts.stream().map(NameRelationRulesTest::str).collect(Collectors.toSet()));
    // the order of the pair must not matter
    assertEquals(Set.of(expected), NameRelationRules.check(n2, n1, rels).stream().map(NameRelationRulesTest::str).collect(Collectors.toSet()));
  }

  static String str(Verdict v) {
    return (v.remove() ? "remove " : "flag ") + v.rel().type() + " " + v.rel().from() + ">" + v.rel().to() + " " + v.issue();
  }

  static void assertPlausible(String n1, String n2, boolean expected) throws Exception {
    var name1 = sp(n1);
    var name2 = sp(n2);
    assertEquals(n1 + " vs " + n2, expected, NameRelationRules.isPlausibleSpellingCorrection(name1, name2));
    assertEquals(n2 + " vs " + n1, expected, NameRelationRules.isPlausibleSpellingCorrection(name2, name1));
  }
}
