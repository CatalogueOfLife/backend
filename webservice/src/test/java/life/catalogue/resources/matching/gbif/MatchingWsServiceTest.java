package life.catalogue.resources.matching.gbif;

import life.catalogue.api.model.Classification;
import life.catalogue.api.vocab.MatchType;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The test cases of matching-ws' MatchingServiceIT (https://github.com/gbif/matching-ws, Apache License 2.0),
 * keeping their expected keys and match types, without the confidence ranges.
 * A case ChecklistBank does not pass is marked with {@link Known}: a gap it still has, or a deliberate difference.
 * Changed is only the Solanum case of testAuthorshipMatchingGIASIP, which asserts the accepted name of two
 * equally fitting synonyms. Not ported is testSimilarButSpanRank, which tests an internal of matching-ws' scoring.
 */
public class MatchingWsServiceTest extends MatchingWsTestBase {

  /**
   * A single character dropped from an epithet shifts its remaining letters along. That used to
   * decide whether the epithet was scored at all, so whether a typo matched depended only on where
   * in the word it fell. Here "rufi" and "ruip" differ by 2 edits while the whole epithet differs by 1.
   * See https://github.com/gbif/matching-ws/issues/13
   */
  @Test
  public void testSingleCharacterEpithetTypoMatches() {
    Classification cl = cl();
    var correct = query(null, "Ablabera rufipes", null, cl, false);
    assertEquals(MatchType.EXACT.name(), correct.diagnostics.matchType);

    // the typo matches the same usage
    known(Known.FUZZY); assertMatch("Ablabera ruipes", cl, correct.usage.key);
  }

  /**
   * Nothing was matched, so there is no match to report a confidence for. Reporting a high
   * confidence for a NONE match reads as a very certain match to callers.
   * See https://github.com/gbif/matching-ws/issues/13 and https://github.com/CatalogueOfLife/checklistbank/issues/253
   */
  @Test
  public void testNoMatchHasNoConfidence() {
    for (String name : List.of("Xqzwklerty Vbnmasdfgh", "Anmeplues", "BOLD:AAX3688")) {
      var m = match(name, cl());
      assertEquals(name, MatchType.NONE.name(), m.diagnostics.matchType);
      assertNull("no confidence expected for " + name, m.diagnostics.confidence);
    }
  }

  @Test
  public void testMatching() {
    Classification cl = cl();
    assertMatch("Anephlus", cl, "1100135");
    assertMatch("Aneplus", cl, "1100050");

    cl.setKingdom("Animalia");
    cl.setClass_("Insecta");
    assertMatch("Aneplus", cl, "1100050");

    // genus Aneplus is order=Coleoptera, but Anelus is a Spirobolida in class Diplopoda
    cl.setClass_("Diplopoda");
    cl.setOrder("Spirobolida");
    known(Known.FUZZY); assertMatch("Aneplus", cl, "1027792");

    cl.setFamily("Atopetholidae");
    known(Known.FUZZY); assertMatch("Aneplus", cl, "1027792");

    assertNoMatch("Anmeplues", cl());
  }

  /**
   * Sabia parviflora is a plant which is in our backbone badly classified as an animal. Assure
   * those names get matched to the next highest taxon that indeed is a plant when the plant kingdom is requested.
   */
  @Test
  public void testBadPlantKingdom() {
    Classification cl = cl();
    // without kingdom snap to the bad animal record
    assertMatch("Sabia parviflora", cl, 7268473);

    // hit the plant family
    assertMatch("Sabiaceae", cl, 2409);

    // make sure its the family
    cl.setKingdom("Plantae");
    cl.setFamily("Sabiaceae");
    assertMatch("Sabia parviflora", cl, 2409);

    // without kingdom snap to the bad animal record
    cl = cl();
    assertMatch("Tibetia tongolensis", cl, 7301567);

    // hit the plant family
    assertMatch("Fabaceae", cl, 5386);

    // make sure its the family
    cl.setKingdom("Plantae");
    cl.setFamily("Fabaceae");
    assertMatch("Tibetia tongolensis", cl, 5386);
  }

  @Test
  public void testHomonyms() {
    // Oenanthe
    Classification cl = cl();
    assertNoMatch("Oenanthe", cl);

    cl.setKingdom("Animalia");
    assertMatch("Oenanthe", cl, 2492483);

    cl.setKingdom("Plantae");
    assertMatch("Oenanthe", cl, 3034893);
    known(Known.FUZZY); assertMatch("Oenante", cl, 3034893);

    // Acanthophora
    cl = cl();
    assertNoMatch("Acanthophora", cl);

    // there are 3 genera in animalia, 2 synonyms and 1 accepted but they differ at phylum level
    cl.setKingdom("Animalia");
    known(Known.HOMONYMS); assertMatch("Acanthophora", cl, 1);

    // now try with molluscs to just get the doubtful one
    cl.setPhylum("Porifera");
    assertMatch("Acanthophora", cl, 3251480);

    cl.setKingdom("Plantae"); // there are multiple plant genera, this should match to plantae now
    known(Known.HOMONYMS); assertMatch("Acanthophora", cl, 6);

    cl.setFamily("Araliaceae");
    assertMatch("Acanthophora", cl, 3036337);
    known(Known.FUZZY); assertMatch("Acantophora", cl, 3036337); // fuzzy match
    // try matching with authors
    known(Known.FUZZY); assertMatch("Acantophora Merrill", cl, 3036337); // fuzzy match

    cl.setFamily("Rhodomelaceae");
    assertMatch("Acanthophora", cl, 2659277);

    // species match
    cl = cl();
    assertMatch("Puma concolor", cl, 2435099);

    cl.setGenus("Puma");
    assertMatch("P. concolor", cl, 2435099);

    cl.setKingdom("Animalia");
    assertMatch("P. concolor", cl, 2435099);

    cl.setKingdom("Pllllaaaantae");
    assertMatch("Puma concolor", cl, 2435099);

    // we now match to the kingdom even though it was given wrong
    // sideeffect of taking kingdoms extremely serious due to bad backbone
    cl.setKingdom("Plantae");
    known(Known.KINGDOM); assertMatch("Puma concolor", cl, 6);

    // Picea concolor is a plant, but this fuzzy match is too far off
    known(Known.KINGDOM); assertMatch("Pima concolor", cl, 6);
    // this one should match though
    known(Known.FUZZY); assertMatch("Pica concolor", cl, 5284657);
    // and this will go to the family
    cl.setFamily("Pinaceae");
    assertMatch("Pima concolor", cl, 3925);

    // Amphibia is a homonym genus, but also and most prominently a class!
    cl = cl();
    // non existing "species" name. Amphibia could either be the genus or the class
    known(Known.HOMONYMS); assertNoMatch("Amphibia eyecount", cl);

    // first try a match against the algae genus
    cl.setKingdom("Plantae");
    cl.setClass_("Rhodophyceae");
    assertMatch("Amphibia eyecount", cl, 2659299);

    // now try with the animal class
    cl.setKingdom("Animalia");
    cl.setClass_("Amphibia");
    assertMatch("Amphibia eyecount", cl, 131);
  }

  @Test
  public void testHomonyms2() {
    // this hits 2 species synonyms with no such name being accepted
    // nub match must pick one if the accepted name of all synonyms is the same!
    Classification cl = cl();
    cl.setKingdom("Plantae");
    cl.setFamily("Poaceae");
    assertMatch("Elytrigia repens", cl, 2706649);
  }

  /**
   * Conservative enough when choosing an equally matched option. The data for this test is in nub297.json.
   * See https://github.com/gbif/checklistbank/issues/295 and https://discourse.gbif.org/t/millipedes-in-the-ocean/3991
   */
  @Test
  public void testHomonyms3() {
    known(Known.HOMONYMS); assertMatch("Siphonophora", cl(), 1);
  }

  @Test
  public void testAuthorshipMatching() {
    Classification cl = cl();
    assertMatch("Prunella alba", cl, 5608009);

    assertMatch("Prunella alba Pall. ex M.Bieb.", cl, 5608009);
    assertMatch("Prunella alba M.Bieb.", cl, 5608009);

    assertMatch("Prunella alba Pall.", cl, 5608009);
    assertMatch("Prunella alba Döring", cl, 5608009);

    // 2 homonyms exist
    assertMatch("Elytrigia repens", cl, 2706649);
    assertMatch("Elytrigia repens Desv.", cl, 7522774);
    assertMatch("Elytrigia repens Nevski", cl, 2706649);
    assertMatch("Elytrigia repens (L.) Desv.", cl, 7522774);
    assertMatch("Elytrigia repens (L.) Nevski", cl, 2706649);

    // very different author, match to genus only
    assertMatch("Elytrigia repens Karimba", cl, 7826764, MatchType.HIGHERRANK);

    // basionym author is right, but otherwise the authorship differs clearly. Match to genus only
    assertMatch("Elytrigia repens (L.) Karimba", cl, 7826764, MatchType.HIGHERRANK);
  }

  /**
   * Names that were different between classic species match and the author aware "lookup":
   * Bromus sterilis, Daphnia, Carpobrotus edulis, Celastrus orbiculatus, Python molurus subsp. bivittatus,
   * Ziziphus mauritiana orthacantha, Solanum verbascifolium auriculatum
   */
  @Test
  public void testAuthorshipMatchingGIASIP() {
    Classification cl = cl();
    assertMatch("Bromus sterilis", cl, 8341523);
    assertMatch("Bromus sterilis Guss.", cl, 8341523);
    assertMatch("Bromus sterilis Gus", cl, 8341523);

    assertMatch("Bromus sterilis L.", cl, 2703647);
    assertMatch("Bromus sterilis Linne", cl, 2703647);

    assertMatch("Bromus sterilis Kumm. & Sendtn.", cl, 7874095);
    assertMatch("Bromus sterilis Kumm", cl, 7874095);
    assertMatch("Bromus sterilis Sendtn.", cl, 7874095);

    assertMatch("Daphnia", cl, 2234785);
    assertMatch("Daphnia Müller, 1785", cl, 2234785);
    assertMatch("Daphnia Müller", cl, 2234785);

    assertMatch("Daphne Müller, 1785", cl, 2234879);
    assertMatch("Daphne Müller, 1776", cl, 2234879);

    known(Known.FUZZY); assertMatch("Daphnia Korth", cl, 3626852);
    cl.setKingdom("Plantae");
    cl.setFamily("Oxalidaceae");
    known(Known.FUZZY); assertMatch("Daphnia Korth", cl, 3626852);
    cl = cl();

    assertMatch("Daphnia Rafinesque", cl, 7956551);
    cl.setKingdom("Animalia");
    cl.setFamily("Calanidae");
    known(Known.FUZZY); assertMatch("Daphnia Rafinesque", cl, 4333792);
    cl = cl();

    assertMatch("Carpobrotus edulis", cl, 3084842);
    assertMatch("Carpobrotus edulis N. E. Br.", cl, 3084842);
    assertMatch("Carpobrotus edulis L.Bolus", cl, 7475472);
    assertMatch("Carpobrotus edulis Bolus", cl, 7475472);
    assertMatch("Carpobrotus dulcis Bolus", cl, 3703510);
    // once again with classification given
    cl.setKingdom("Plantae");
    cl.setFamily("Celastraceae");
    assertMatch("Carpobrotus edulis", cl, 3084842);
    assertMatch("Carpobrotus edulis N. E. Br.", cl, 3084842);
    assertMatch("Carpobrotus edulis L.Bolus", cl, 7475472);
    assertMatch("Carpobrotus edulis Bolus", cl, 7475472);
    assertMatch("Carpobrotus dulcis Bolus", cl, 3703510);
    cl = cl();

    assertMatch("Celastrus orbiculatus", cl, 8104460);
    assertMatch("Celastrus orbiculatus Murray", cl, 8104460);
    known(Known.AUTHOR_TIE); assertMatch("Celastrus orbiculatus Thunb", cl, 3169169);
    assertMatch("Celastrus orbiculatus Lam", cl, 7884995);

    assertMatch("Python molurus subsp. bivittatus", cl, 6162891);
    assertMatch("Python molurus bivittatus", cl, 6162891);
    assertMatch("Python molurus bivittatus Kuhl", cl, 6162891);
    assertMatch("Python molurus subsp. bibittatus", cl, 4287608);

    assertMatch("Ziziphus mauritiana orthacantha", cl, 7786586);
    assertMatch("Ziziphus mauritiana ssp. orthacantha", cl, 7786586);
    assertMatch("Ziziphus mauritiana ssp. orthacantha Chev.", cl, 7786586);
    assertMatch("Ziziphus mauritiana var. orthacantha", cl, 8068734);
    assertMatch("Ziziphus mauritiana var. orthacantha Chev.", cl, 8068734);

    // matching-ws expects 2930718, (Aiton) Hassl., but both subspecies are synonyms of the same accepted name 2931469
    // and the name gives no authorship to pick one: assert the accepted name
    assertAccepted("Solanum verbascifolium auriculatum", cl, 2931469);
    assertAccepted("Solanum verbascifolium ssp. auriculatum", cl, 2931469);
    assertMatch("Solanum verbascifolium var. auriculatum Kuntze", cl, 8363606);
    assertMatch("Solanum verbascifolium var. auriculatum Maiden", cl, 6290014);
    assertMatch("Solanum verbascifolium var. auriculatum", cl, 6290014);
  }

  @Test
  public void testOtuMatching() {
    Classification cl = cl();

    assertMatch("SH205817.07FU", cl, 9732858);

    var m = assertMatch("BOLD:AAX3687", cl, 993172099);
    assertEquals("BOLD:AAX3687", m.usage.name);

    assertMatch("SH021315.07FU", cl, 993730906);

    cl.setFamily("Maldanidae");
    assertMatch("BOLD:AAX3687", cl, 993172099);
    assertMatch("bold:aax3687", cl, 993172099);

    assertNoMatch("BOLD:AAX3688", cl);
    assertNoMatch("BOLD:AAY3687", cl);
    assertNoMatch("COLD:AAX3687", cl);
    assertNoMatch("AAX3687", cl);
  }

  /**
   * http://dev.gbif.org/issues/browse/PF-2574
   * Inachis io (Linnaeus, 1758), Inachis io NPV, Dionychopus amasis GV,
   * Hyloicus pinastri NPV, Hylobius pinastri Billberg, Hylobius (Hylobius) pinastri Escherich, 1923,
   * Vanessa cardui NPV, Vanessa cardui (Linnaeus, 1758)
   */
  @Test
  public void testViruses() {
    Classification cl = cl();
    assertMatch("Inachis io", cl, 5881450);
    assertMatch("Inachis io (Linnaeus)", cl, 5881450);
    assertMatch("Inachis io NPV", cl, 8562651);

    assertMatch("Dionychopus amasis GV", cl, 6876449);
    // to lepidoptera genus only
    assertMatch("Dionychopus amasis", cl, 4689754);
    // with given kingdom result in no match, GV is part of the name
    cl.setKingdom("Virus");
    assertNoMatch("Dionychopus amasis", cl);
  }

  /**
   * Non existing species with old family classification should match genus Linaria.
   * http://dev.gbif.org/issues/browse/POR-2704
   */
  @Test
  public void testPOR2704() {
    Classification cl = cl();
    cl.setKingdom("Plantae");
    cl.setFamily("Scrophulariaceae"); // nowadays Plantaginaceae as in our nub/col
    assertMatch("Linaria pedunculata (L.) Chaz.", cl, 3172168);
  }

  /**
   * Classification names need to be parsed if they are no monomials already
   */
  @Test
  public void testClassificationWithAuthors() {
    Classification cl = cl();
    cl.setKingdom("Fungi Bartling, 1830");
    cl.setPhylum("Ascomycota Caval.-Sm., 1998");
    cl.setClass_("Lecanoromycetes, O.E. Erikss. & Winka, 1997");
    cl.setOrder("Lecanorales, Nannf., 1932");
    cl.setFamily("Ramalinaceae, C. Agardh, 1821");
    cl.setGenus("Toninia");
    assertMatch("Toninia aromatica", cl, 2608009);
  }

  /**
   * Non existing species should match genus Quedius http://dev.gbif.org/issues/browse/POR-1712
   */
  @Test
  public void testPOR1712() {
    Classification cl = cl();
    cl.setClass_("Hexapoda");
    cl.setFamily("Staphylinidae");
    cl.setGenus("Quedius");
    cl.setKingdom("Animalia");
    cl.setPhylum("Arthropoda");
    assertMatch("Quedius caseyi divergens", cl, 4290501);
  }

  /**
   * Indet subspecies should match to species Panthera pardus http://dev.gbif.org/issues/browse/POR-2701
   */
  @Test
  public void testPOR2701() {
    Classification cl = cl();
    cl.setPhylum("Chordata");
    cl.setClass_("Mammalia");
    cl.setOrder("Carnivora");
    cl.setFamily("Felidae");
    cl.setGenus("Panthera");
    assertMatch("Panthera pardus ssp.", cl, 5219436);
  }

  /**
   * Brunella alba Pallas ex Bieb.(Labiatae, Plantae) is wrongly matched to Brunerella alba R.F. Castañeda &
   * Vietinghoff (Fungi). The species does not exist in the nub and the genus Brunella is a synonym of Prunella.
   * Match to synonym genus Brunella http://dev.gbif.org/issues/browse/POR-2684
   */
  @Test
  public void testPOR2684() {
    Classification cl = cl();
    cl.setKingdom("Plantae");
    cl.setFamily("Labiatae");
    cl.setGenus("Brunella");
    assertMatch("Brunella alba Pallas ex Bieb.", cl, 6008586);
  }

  /**
   * The wasp species does not exist and became a spider instead. Should match to the wasp genus.
   * http://dev.gbif.org/issues/browse/POR-2469
   */
  @Test
  public void testPOR2469() {
    Classification cl = cl();
    cl.setKingdom("Animalia");
    cl.setPhylum("Arthropoda");
    cl.setClass_("Insecta");
    cl.setOrder("Hymenoptera");
    cl.setFamily("Tiphiidae");
    cl.setGenus("Eirone");
    assertMatch("Eirone neocaledonica Williams", cl, 4674090);
  }

  /**
   * Allow lower case names https://github.com/gbif/portal-feedback/issues/1379
   */
  @Test
  public void testFeedback1379() {
    Classification cl = cl();
    cl.setFamily("Helicidae");
    assertMatch("iberus gualtieranus", cl, 4564258);
    known(Known.FUZZY); assertMatch("Iberus gualterianus", cl, 4564258);
  }

  /**
   * The beetle Oreina elegans does not exist in the nub and became a spider instead. Should match to the genus.
   * http://dev.gbif.org/issues/browse/POR-2607
   */
  @Test
  public void testPOR2607() {
    Classification cl = cl();
    cl.setKingdom("Animalia");
    cl.setFamily("Chrysomelidae");
    assertMatch("Oreina", cl, 6757727);
    known(Known.HOMONYMS); assertMatch("Oreina elegans", cl, 6757727);

    cl.setPhylum("Arthropoda");
    cl.setClass_("Insecta");
    cl.setOrder("Coleoptera");
    assertMatch("Oreina", cl, 6757727);
    known(Known.HOMONYMS); assertMatch("Oreina elegans", cl, 6757727, MatchType.HIGHERRANK);
  }

  /**
   * indet is used in occurrences not as an epithet but to indicate indeterminate names, like spec. is used.
   * Make sure we never fuzzy match those, but allow for exact matches as there are a few true names out there.
   * Peperomia indet != Peperomia induta, Lacerta bilineata indet (Elbing, 2001) good!
   */
  @Test
  public void testIndet() {
    Classification cl = cl();
    // matching-ws has these two cases commented out:
    // assertMatch("Peperomia induta", cl, 4189260);
    // assertMatch("Peperomia indet", cl, 3086367, MatchType.HIGHERRANK);
    known(Known.INDET); assertMatch("Lacerta bilineata indet", cl, 6159243);
  }

  /**
   * http://gbif.blogspot.com/2015/03/improving-gbif-backbone-matching.html
   */
  @Test
  public void testBlogNames() {
    // http://www.gbif.org/occurrence/164267402/verbatim
    Classification cl = cl();
    assertMatch("Xysticus sp.", cl, 2164999, MatchType.HIGHERRANK);
    assertMatch("Xysticus spec.", cl, 2164999, MatchType.HIGHERRANK);

    // http://www.gbif.org/occurrence/1061576151/verbatim
    cl = cl();
    cl.setFamily("Poaceae");
    cl.setGenus("Triodia");
    assertMatch("Triodia sp.", cl, 2702695);

    // http://www.gbif.org/occurrence/1037140379/verbatim
    cl = cl();
    cl.setKingdom("Plantae");
    cl.setFamily("XYRIDACEAE");
    cl.setGenus("Xyris");

    // only to the genus 2692599
    // Xyris jolyi Wand. & Cerati 2692999
    assertMatch("Xyris kralii Wand.", cl, 2692599, MatchType.HIGHERRANK);

    // http://www.gbif.org/occurrence/144904719/verbatim
    cl = cl();
    cl.setKingdom("Plantae");
    cl.setFamily("GRAMINEAE");
    assertMatch("Zea mays subsp. parviglumis var. huehuet Iltis & Doebley", cl, 5290052);
  }

  @Test
  public void testImprovedMatching() {
    // http://www.gbif.org/occurrence/164267402/verbatim
    Classification cl = cl();
    assertMatch("Zabidius novemaculeatus", cl, 2394331);
    known(Known.FUZZY); assertMatch("Zabideus novemaculeatus", cl, 2394331);

    cl.setFamily("Ephippidae");
    assertMatch("Zabidius novemaculeatus", cl, 2394331);
    known(Known.FUZZY); assertMatch("Zabidius novaemaculeatus", cl, 2394331);
    known(Known.FUZZY); assertMatch("Zabidius novaemaculeata", cl, 2394331);
    // no name normalization on the genus, but a fuzzy match
    known(Known.FUZZY); assertMatch("Zabideus novemaculeatus", cl, "2394331", MatchType.VARIANT);

    cl = cl();
    cl.setKingdom("Animalia");
    cl.setFamily("Yoldiidae");
    // genus match only
    assertMatch("Yoldia bronni", cl, 2285488);

    cl.setFamily("Nuculanidae");
    // genus match only
    assertMatch("Yoldia frate", cl, 2285488);
  }

  /**
   * Names that fuzzy match to higher species "Iberus gualtieranus"
   */
  @Test
  public void testIberusGualtieranus() {
    Classification cl = cl();
    known(Known.FUZZY); assertMatch("Iberus gualterianus minor Serradell", cl, 4564258);

    cl.setFamily("Helicidae");
    known(Known.FUZZY); assertMatch("Iberus gualterianus minor Serradell", cl, 4564258);
  }

  /**
   * https://github.com/gbif/portal-feedback/issues/2930
   */
  @Test
  public void higherOverFuzzy() {
    Classification cl = cl();
    // Stolas
    var m = assertMatch("Stolas costaricensis", cl, 4734997);
    assertEquals(MatchType.HIGHERRANK.name(), m.diagnostics.matchType);

    cl.setKingdom("Animalia");
    cl.setPhylum("Arthropoda");
    cl.setClass_("Insecta");

    // Stelis costaricensis
    known(Known.FUZZY); assertMatch("Stolas costaricensis", cl, 1334265, MatchType.VARIANT);

    // Stolas again
    cl.setOrder("Coleoptera");
    cl.setFamily("Chrysomelidae");
    assertMatch("Stolas costaricensis", cl, 4734997, MatchType.HIGHERRANK);

    // exclude Stolas, match Stelis costaricensis again but with lower confidence now
    known(Known.EXCLUDE); assertMatch("Stolas costaricensis", cl, 1334265, MatchType.VARIANT, Set.of("7780")); // excludes Stolas family
  }

  /**
   * https://github.com/gbif/checklistbank/issues/192
   */
  @Test
  public void subgenusJacea() {
    Classification cl = cl();
    cl.setKingdom("Plantae");
    // THIS SETS the genus too
    known(Known.GENUS_GROUP); var m = assertMatch("Centaurea subg. Jacea", cl, 3148240, MatchType.EXACT);
    if (m.usage != null) {
      assertEquals("Jacea", m.usage.canonicalName);
    }
  }

  /**
   * https://github.com/gbif/checklistbank/issues/196
   */
  @Test
  public void mycenaFllavoalba() {
    Classification cl = cl();
    cl.setKingdom("Fungi");
    cl.setPhylum("Basidiomycota");
    cl.setClass_("Agaricomycetes");
    cl.setOrder("Agaricales");
    cl.setFamily("Mycenaceae");
    cl.setGenus("Mycena");
    cl.setSpecies("flavoalba");

    var m = assertMatch("Mycena flavoalba (Fr.) Quel.", cl, 4911770, MatchType.EXACT);
    assertEquals("Mycena flavoalba", m.usage.canonicalName);

    assertMatch("Mycena flavoalba (Fr.) Quél.", cl, 4911770, MatchType.EXACT);
  }

  /**
   * https://github.com/gbif/checklistbank/issues/200
   */
  @Test
  public void merganser() {
    Classification cl = cl();

    var m = assertMatch("Mergus merganser Linnaeus, 1758", cl, 2498370, MatchType.EXACT);
    assertEquals("Mergus merganser", m.usage.canonicalName);

    // All uppper case, https://github.com/gbif/checklistbank/issues/175
    m = assertMatch("MERGUS MERGANSER", cl, 2498370, MatchType.EXACT);
    assertEquals("Mergus merganser", m.usage.canonicalName);
  }

  /**
   * https://github.com/gbif/checklistbank/issues/175
   */
  @Test
  public void otuIncaseInsensitive() {
    Classification cl = cl();

    assertMatch("AAA536-G10 sp003284565", cl, 10701019, MatchType.EXACT);
    assertMatch("aaa536-g10 sp003284565", cl, 10701019, MatchType.EXACT);
    assertMatch("Aaa536-g10 sp003284565", cl, 10701019, MatchType.EXACT);
  }

  @Test
  public void npe() {
    Classification cl = cl();

    assertNoMatch(null, cl);
    assertNoMatch("", cl);
    assertNoMatch("null", cl);
  }

  @Test
  public void dinosaura() {
    Classification cl = cl();
    cl.setKingdom("Animalia");
    cl.setPhylum("Chordata");
    cl.setClass_("Reptilia");

    // we dont have dinosauria, just a bad genus match Dinosaura (4819911) and a higher match Reptilia (358)
    assertMatch("Dinosauria", Rank.CLASS, cl, 358, MatchType.HIGHERRANK);
    known(Known.FUZZY); assertMatch("Dinosauria", Rank.GENUS, cl, 4819911, MatchType.VARIANT);
    assertMatch("Dinosaura", Rank.GENUS, cl, 4819911, MatchType.EXACT);
  }

  /**
   * https://github.com/gbif/checklistbank/issues/247
   */
  @Test
  public void testCommonHigherDenomiator() {
    Classification cl = cl();
    cl.setKingdom("Animalia");

    known(Known.HOMONYMS); assertMatch("Jaspidia deceptoria Scopoli, 1763", Rank.FAMILY, cl, 7015, MatchType.HIGHERRANK);
  }

  /**
   * https://github.com/gbif/portal-feedback/issues/4532
   */
  @Test
  public void testAuthorBrackets() {
    Classification cl = cl();
    cl.setKingdom("Animalia");

    assertMatch("Eristalis lineata Harris, 1776", Rank.SPECIES, cl, 7834133, MatchType.EXACT);
    assertMatch("Eristalis lineata (Harris, 1776)", Rank.SPECIES, cl, 7834133, MatchType.EXACT);
  }

  /**
   * https://github.com/gbif/portal-feedback/issues/2935
   */
  @Test
  public void aggregates() {
    Classification cl = cl();
    cl.setKingdom("Animalia");
    cl.setOrder("Diptera");
    cl.setFamily("Clusiidae");

    assertMatch("Clusiodes melanostomus", Rank.SPECIES, cl, 4295121, MatchType.EXACT);
    known(Known.RANK_RETRY); assertMatch("Clusiodes melanostomus", Rank.SPECIES_AGGREGATE, cl, 1550465, MatchType.HIGHERRANK);
  }

  /**
   * https://github.com/CatalogueOfLife/data/issues/743
   */
  @Test
  public void libellulidae() {
    Classification cl = cl();

    assertMatch("Libellulidae", Rank.FAMILY, cl, 5936, MatchType.EXACT);
    assertMatch("Libellulidae Leach, 1815", Rank.FAMILY, cl, 5936, MatchType.EXACT);
    assertMatch("Libellulidae Rambur, 1842", Rank.FAMILY, cl, 5936, MatchType.EXACT);
  }

  /**
   * https://github.com/gbif/checklistbank/issues/280
   */
  @Test
  public void iris() {
    Classification cl = cl();
    cl.setKingdom("Animalia");
    cl.setPhylum("Chordata");
    cl.setClass_("Aves");
    cl.setOrder("Passeriformes");
    cl.setFamily("Pipridae");
    cl.setGenus("Lepidothrix");
    cl.setSpecies("iris");
    // we still have old nub data in the lookup resources, hence the match goes wrong!
    assertMatch("iris", null, cl, 5230524, MatchType.EXACT);
  }

  /**
   * https://github.com/gbif/checklistbank/issues/289
   */
  @Test
  public void shortCircuitUsageKey() {
    Classification cl = cl();
    cl.setKingdom("Animalia");
    cl.setGenus("Ablabera");
    cl.setSpecies("rufipes");
    String goodKey = "5230524"; // Lepidothrix iris
    String badKey = "99999999";
    // names are ignored and a key is either found, or not found at all in which case there is no confidence to report
    assertMatch(goodKey, "Ablabera rufipes", Rank.SPECIES, cl, goodKey, MatchType.EXACT, false);
    assertMatch(badKey, "Ablabera rufipes", Rank.SPECIES, cl, null, MatchType.NONE, false);
  }

  /**
   * https://github.com/CatalogueOfLife/data/issues/725
   */
  @Test
  public void polygalaVulgaris() {
    Classification cl = cl();
    cl.setKingdom("Plantae");
    cl.setClass_("Equisetopsida");
    cl.setOrder("Fabales");
    cl.setFamily("Polygalaceae");
    assertMatch("Polygala vulgaris L., 1753 [nom. et typ. cons.]", Rank.SPECIES, cl, "4L8K5", MatchType.EXACT);
    assertMatch("Polygala vulgaris subsp. vulgaris L., 1753", Rank.SUBSPECIES, cl, "7KNMP", MatchType.EXACT);
  }
}
