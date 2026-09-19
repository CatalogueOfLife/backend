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

  @Test
  public void differentBasionymAndCombinationAuthorsTellUsNothing() {
    // a basionym author can only ever be lined up against a combination author as a guess: agreeing confirms,
    // disagreeing is no evidence either way
    assertEquals(PLAUSIBLE, ev(f(SPECIES, "(Smith, 1900)", ACCEPTED), f(SPECIES, "Jones, 1900", ACCEPTED)));
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
    // Oenanthe the bird versus Oenanthe the plant
    var bird = new NameIdentity.Facts(GENUS, "Vieillot, 1816", null, ACCEPTED, NomCode.ZOOLOGICAL, null, null);
    var plant = new NameIdentity.Facts(GENUS, "Vieillot, 1816", null, ACCEPTED, NomCode.BOTANICAL, null, null);
    assertEquals(CONTRADICTED, identity.compare(bird, plant).evidence);
  }

  @Test
  public void disparateTaxGroupsContradict() {
    var animal = new NameIdentity.Facts(GENUS, "Mill.", null, ACCEPTED, null, TaxGroup.Insects, null);
    var plant = new NameIdentity.Facts(GENUS, "Mill.", null, ACCEPTED, null, TaxGroup.Angiosperms, null);
    assertEquals(CONTRADICTED, identity.compare(animal, plant).evidence);
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
