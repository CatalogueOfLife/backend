package life.catalogue.assembly;

import life.catalogue.api.model.Name;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class NameBlocklistTest {

  static Name name(String sciname, String authorship) {
    return Name.newBuilder().scientificName(sciname).authorship(authorship).rank(Rank.SPECIES).build();
  }

  @Test
  public void namesWithAndWithoutAuthorship() {
    var bl = new NameBlocklist(List.of("amara AENEA", "Bus cus L."), null);
    assertTrue(bl.isBlocked(name("Amara aenea", "(De Geer, 1774)")));
    assertTrue(bl.isBlocked(name("Bus cus", "L.")));
    assertFalse(bl.isBlocked(name("Bus cus", "Mill.")));
    assertFalse(bl.isBlocked(name("Amara familiaris", null)));
  }

  @Test
  public void patternsAreFoundAnywhereInTheLabel() {
    var bl = new NameBlocklist(null, List.of("^incertae", "sp\\. ?nov"));
    assertTrue(bl.isBlocked(name("Incertae sedis", null)));
    assertTrue(bl.isBlocked(name("Aus sp. nov", null)));
    assertFalse(bl.isBlocked(name("Aus bus", null)));
  }

  @Test
  public void invalidPatternsAreSkipped() {
    var bl = new NameBlocklist(null, List.of("Aus (bus", "^Cus"));
    assertTrue(bl.isBlocked(name("Cus dus", null)));
    assertFalse(bl.isBlocked(name("Aus (bus", null)));
  }

  @Test
  public void empty() {
    assertTrue(new NameBlocklist(null, List.of(" ")).isEmpty());
    assertFalse(new NameBlocklist(List.of("Aus"), null).isEmpty());
  }
}
