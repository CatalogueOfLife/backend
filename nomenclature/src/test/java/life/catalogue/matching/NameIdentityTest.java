package life.catalogue.matching;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static life.catalogue.api.vocab.TaxonomicStatus.*;
import static life.catalogue.matching.NameIdentity.Evidence.*;
import static org.gbif.nameparser.api.Rank.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NameIdentityTest {

  final NameIdentity identity = new NameIdentity();

  static NameIdentity.Facts f(Rank rank, String authorship, TaxonomicStatus status) {
    return new NameIdentity.Facts(rank, authorship, null, status, null, null, null);
  }

  NameIdentity.Evidence ev(NameIdentity.Facts a, NameIdentity.Facts b) {
    return identity.compare(a, b).evidence;
  }

  @Test
  public void sameNameIsConfirmed() {
    assertEquals(CONFIRMED, ev(f(SPECIES, "Mill.", ACCEPTED), f(SPECIES, "Mill.", ACCEPTED)));
  }

  @Test
  public void authorshipSpellingIsNotADifference() {
    // what AuthorComparator buys us over the exact string comparison the id provider used to do
    assertEquals(CONFIRMED, ev(f(SPECIES, "Mill.", ACCEPTED), f(SPECIES, "Miller", ACCEPTED)));
    assertEquals(CONFIRMED, ev(f(SPECIES, "L.", ACCEPTED), f(SPECIES, "Linné", ACCEPTED)));
  }

  @Test
  public void citationVariantsOfOneAuthorAreEqualEvidence() {
    // AuthorComparator accepts years up to 11 apart and a combination author lined up against the same basionym
    // author. An exact copy of a citation is deliberately no better evidence than such a variant of it: which of their
    // ids a name keeps is left to seniority, so a removed duplicate cannot keep its junior id by being cited its own way
    var exact = identity.compare(f(SUBSPECIES, "Bryk, 1948", ACCEPTED), f(SUBSPECIES, "Bryk, 1948", ACCEPTED));
    var variant = identity.compare(f(SUBSPECIES, "Bryk, 1948", ACCEPTED), f(SUBSPECIES, "(Bryk, 1949)", ACCEPTED));
    assertEquals(CONFIRMED, exact.evidence);
    assertEquals(exact, variant);
    assertEquals(exact, identity.compare(f(SPECIES, "Dyar, 1902", ACCEPTED), f(SPECIES, "Dyar, 1899", ACCEPTED)));
  }

  @Test
  public void yearsTooFarApartContradict() {
    assertEquals(CONTRADICTED, ev(f(SPECIES, "Dyar, 1880", ACCEPTED), f(SPECIES, "Dyar, 1902", ACCEPTED)));
  }

  /**
   * An unbracketed author is the original author, whatever the code. Against the other side's bracketed author it is
   * the same name cited with or without brackets, or two different original authors - which is how zoological
   * catalogues cite a later user of a name as its author, sometimes for a misidentification of another species.
   * The redirect backfill dry run of 2026-11-10 paired 23,738 such names before.
   */
  @Test
  public void differentAuthorsAcrossBracketsContradict() {
    var v = identity.compare(f(SPECIES, "Harrington, 1899", SYNONYM), f(SPECIES, "(Kirby, 1837)", SYNONYM));
    assertEquals(CONTRADICTED, v.evidence);
    assertEquals(NameIdentity.Conflict.AUTHORSHIP, v.conflict);
    assertEquals(Equality.PROBABLY_DIFFERENT, v.authorship.equality());
    assertEquals("authorship different author across brackets", v.explain());
    assertEquals(CONTRADICTED, ev(f(SPECIES, "(Smith, 1900)", ACCEPTED), f(SPECIES, "Jones, 1900", ACCEPTED)));
    assertEquals(CONTRADICTED, ev(f(SPECIES, "Matsumura, 1925", SYNONYM), f(SPECIES, "(Linnaeus, 1758)", ACCEPTED)));
    // the same author, years too far apart for one publication
    var years = identity.compare(f(SPECIES, "Kennel, 1879", ACCEPTED), f(SPECIES, "(Kennel, 1899)", ACCEPTED));
    assertEquals(CONTRADICTED, years.evidence);
    assertEquals("authorship different year across brackets", years.explain());
  }

  @Test
  public void sameAuthorAcrossBracketsIsConfirmed() {
    // brackets added or lost
    var v = identity.compare(f(SPECIES, "Pascoe, 1864", ACCEPTED), f(SPECIES, "(Pascoe, 1859)", ACCEPTED));
    assertEquals(CONFIRMED, v.evidence);
    assertEquals("authorship brackets", v.explain());
    // next to a combination author the same original author is only probably the same name: no contradiction, so an
    // identifier survives a source adding the combination author, but never confirmed - both names often exist
    var cited = identity.compare(f(SPECIES, "L.", ACCEPTED), f(SPECIES, "(L.) Mill.", ACCEPTED));
    assertEquals(PLAUSIBLE, cited.evidence);
    assertEquals("authorship basionym author cited", cited.explain());
    assertEquals(PLAUSIBLE, ev(f(SPECIES, "Kirby, 1837", ACCEPTED), f(SPECIES, "(Kirby, 1837) LeConte, 1850", ACCEPTED)));
    // or the combination author, the basionym author left out
    var omitted = identity.compare(f(SPECIES, "Suksd.", ACCEPTED), f(SPECIES, "(G.S.Torr.) Suksd.", ACCEPTED));
    assertEquals(CONFIRMED, omitted.evidence);
    assertEquals("authorship basionym omitted", omitted.explain());
    // but nothing else
    assertEquals(CONTRADICTED, ev(f(SPECIES, "DC.", ACCEPTED), f(SPECIES, "(L.) Mill.", ACCEPTED)));
  }

  /**
   * COL26.10 has 1,583 pairs like these next to each other, an original name and a later transfer of a basionym of
   * the same author from another genus. Each must keep its own identifier, so the exact authorship has to outrank the
   * probable one.
   */
  @Test
  public void exactAuthorshipOutranksTheBasionymAuthorCited() {
    var original = f(SPECIES, "Baker", SYNONYM);
    var transfer = f(SPECIES, "(Baker) Mez", SYNONYM);
    assertEquals(CONFIRMED, identity.compare(original, f(SPECIES, "Baker", SYNONYM)).evidence);
    assertEquals(PLAUSIBLE, identity.compare(original, transfer).evidence);
    assertTrue(identity.compare(transfer, f(SPECIES, "(Baker) Mez", SYNONYM)).compareTo(identity.compare(transfer, original)) > 0);
  }

  @Test
  public void verdictsExplainThemselves() {
    assertEquals("rank", identity.compare(f(GENUS, "Mill.", ACCEPTED), f(SPECIES, "Mill.", ACCEPTED)).explain());
    assertEquals("misapplied", identity.compare(f(SPECIES, "Mill.", MISAPPLIED), f(SPECIES, "Mill.", ACCEPTED)).explain());
    assertEquals("authorship missing", identity.compare(f(GENUS, null, ACCEPTED), f(GENUS, "Trew", ACCEPTED)).explain());
    var bird = new NameIdentity.Facts(GENUS, "Vieillot, 1816", null, ACCEPTED, NomCode.ZOOLOGICAL, null, null);
    var plant = new NameIdentity.Facts(GENUS, "L.", null, ACCEPTED, NomCode.BOTANICAL, null, null);
    assertEquals("CONTRADICTED/0 authorship different author", identity.compare(bird, plant).toString());
    var unauthored = new NameIdentity.Facts(GENUS, null, null, ACCEPTED, NomCode.BOTANICAL, null, null);
    assertEquals("code, authorship missing", identity.compare(bird, unauthored).explain());
  }

  @Test
  public void addedOrRemovedAuthorshipNeverBlocks() {
    // https://github.com/CatalogueOfLife/backend/issues/1326
    assertEquals(PLAUSIBLE, ev(f(GENUS, null, ACCEPTED), f(GENUS, "Trew", ACCEPTED)));
    assertEquals(PLAUSIBLE, ev(f(GENUS, "Trew", ACCEPTED), f(GENUS, null, ACCEPTED)));
  }

  @Test
  public void changedAuthorshipContradicts() {
    assertEquals(CONTRADICTED, ev(f(SPECIES, "Mill.", ACCEPTED), f(SPECIES, "DC.", ACCEPTED)));
  }

  @Test
  public void unrankedTellsUsNothing() {
    assertEquals(PLAUSIBLE, ev(f(UNRANKED, "Mill.", ACCEPTED), f(SPECIES, "Mill.", ACCEPTED)));
    assertEquals(WEAK, ev(f(UNRANKED, null, ACCEPTED), f(SPECIES, null, ACCEPTED)));
  }

  @Test
  public void differentConcreteRanksContradict() {
    assertEquals(CONTRADICTED, ev(f(GENUS, "Mill.", ACCEPTED), f(SPECIES, "Mill.", ACCEPTED)));
  }

  @Test
  public void sunkIntoSynonymyKeepsItsIdentity() {
    // a taxon becoming a synonym is the single most common taxonomic change there is
    assertEquals(CONFIRMED, ev(f(SPECIES, "Mill.", ACCEPTED), f(SPECIES, "Mill.", SYNONYM)));
    // and provisionally accepted is the same kind of thing as accepted
    assertEquals(CONFIRMED, ev(f(SPECIES, "Mill.", ACCEPTED), f(SPECIES, "Mill.", PROVISIONALLY_ACCEPTED)));
  }

  @Test
  public void misappliedNamesAreTheirOwnWorld() {
    assertEquals(CONTRADICTED, ev(f(SPECIES, "Mill.", MISAPPLIED), f(SPECIES, "Mill.", ACCEPTED)));
    assertEquals(CONTRADICTED, ev(f(SPECIES, "Mill.", SYNONYM), f(SPECIES, "Mill.", MISAPPLIED)));
    assertEquals(CONFIRMED, ev(f(SPECIES, "Mill.", MISAPPLIED), f(SPECIES, "Mill.", MISAPPLIED)));
  }

  @Test
  public void misappliedNamesAreToldApartByTheirPhrase() {
    var a = new NameIdentity.Facts(SPECIES, "Mill.", "sensu Smith", MISAPPLIED, null, null, null);
    var b = new NameIdentity.Facts(SPECIES, "Mill.", "sensu Jones", MISAPPLIED, null, null, null);
    var c = new NameIdentity.Facts(SPECIES, "Mill.", "sensu Smith", MISAPPLIED, null, null, null);
    assertEquals(CONTRADICTED, identity.compare(a, b).evidence);
    assertEquals(CONFIRMED, identity.compare(a, c).evidence);
  }

  @Test
  public void differentCodesContradict() {
    // Oenanthe the bird versus Oenanthe the plant, once without an authorship to tell them apart
    var bird = new NameIdentity.Facts(GENUS, "Vieillot, 1816", null, ACCEPTED, NomCode.ZOOLOGICAL, null, null);
    var plant = new NameIdentity.Facts(GENUS, "L.", null, ACCEPTED, NomCode.BOTANICAL, null, null);
    var unauthoredPlant = new NameIdentity.Facts(GENUS, null, null, ACCEPTED, NomCode.BOTANICAL, null, null);
    assertEquals(CONTRADICTED, identity.compare(bird, plant).evidence);
    assertEquals(CONTRADICTED, identity.compare(bird, unauthoredPlant).evidence);
  }

  @Test
  public void disparateTaxGroupsContradict() {
    var animal = new NameIdentity.Facts(GENUS, "Mill.", null, ACCEPTED, null, TaxGroup.Insects, null);
    var plant = new NameIdentity.Facts(GENUS, null, null, ACCEPTED, null, TaxGroup.Angiosperms, null);
    assertEquals(CONTRADICTED, identity.compare(animal, plant).evidence);
  }

  /**
   * COL XR attempt 632: fungi and diatoms published with the zoological code in one release and the botanical one
   * in the next, and species attached to a homonym genus in another kingdom - Platygaster wasps under Diptera, Ilex
   * under springtails. The name did not change, so the equal authorship keeps it, but only as plausible.
   */
  @Test
  public void equalAuthorshipOverrulesCodeAndGroup() {
    var zoo = new NameIdentity.Facts(SPECIES, "Senwanna, Kodchasee, J. Kumla & N. Suwannar., 2026", null, ACCEPTED,
      NomCode.ZOOLOGICAL, TaxGroup.Ascomycetes, null);
    var bot = new NameIdentity.Facts(SPECIES, "Senwanna, Kodchasee, J. Kumla & N. Suwannar., 2026", null, ACCEPTED,
      NomCode.BOTANICAL, TaxGroup.Ascomycetes, null);
    assertEquals(PLAUSIBLE, identity.compare(zoo, bot).evidence);

    var underFlies = new NameIdentity.Facts(SPECIES, "Buhl, 2017", null, PROVISIONALLY_ACCEPTED, NomCode.ZOOLOGICAL,
      TaxGroup.Diptera, null);
    var underWasps = new NameIdentity.Facts(SPECIES, "Buhl, 2017", null, PROVISIONALLY_ACCEPTED, NomCode.ZOOLOGICAL,
      TaxGroup.Hymenoptera, null);
    var unchanged = new NameIdentity.Facts(SPECIES, "Buhl, 2017", null, PROVISIONALLY_ACCEPTED, NomCode.ZOOLOGICAL,
      TaxGroup.Diptera, null);
    var moved = identity.compare(underFlies, underWasps);
    assertEquals(PLAUSIBLE, moved.evidence);
    // a candidate that stayed in place still ranks higher
    assertTrue(identity.compare(underFlies, unchanged).compareTo(moved) > 0);
  }

  @Test
  public void acceptedNameSeparatesProParteSynonyms() {
    var underAbies = new NameIdentity.Facts(SPECIES, "DC.", null, SYNONYM, null, null, "Abies alba");
    var underLarix = new NameIdentity.Facts(SPECIES, "DC.", null, SYNONYM, null, null, "Larix alba");
    var alsoAbies = new NameIdentity.Facts(SPECIES, "DC.", null, SYNONYM, null, null, "Abies alba");
    // neither is contradicted - the accepted name only corroborates - but the right one corroborates more
    assertEquals(CONFIRMED, identity.compare(underAbies, underLarix).evidence);
    assertTrue(identity.compare(underAbies, alsoAbies).compareTo(identity.compare(underAbies, underLarix)) > 0);
  }

  @Test
  public void verdictsAreOrderedWorstToBest() {
    var weak = identity.compare(f(UNRANKED, null, ACCEPTED), f(SPECIES, null, ACCEPTED));
    var plausible = identity.compare(f(GENUS, null, ACCEPTED), f(GENUS, "Trew", ACCEPTED));
    var confirmed = identity.compare(f(SPECIES, "Mill.", ACCEPTED), f(SPECIES, "Mill.", ACCEPTED));
    assertTrue(weak.compareTo(plausible) < 0);
    assertTrue(plausible.compareTo(confirmed) < 0);
  }
}
