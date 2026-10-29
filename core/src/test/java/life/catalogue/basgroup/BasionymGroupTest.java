package life.catalogue.basgroup;

import org.gbif.nameparser.api.Authorship;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class BasionymGroupTest {

  @Test
  public void getAll() {
    HomotypicGroup<String> group = new HomotypicGroup<>(null, "alba", Authorship.authors("Miller"), null);
    assertTrue(group.isEmpty());
    assertEquals(0, group.size());
    assertEquals(List.of(), group.getAll());

    group.addRecombination("Turnus albus");
    assertFalse(group.isEmpty());
    assertEquals(1, group.size());
    assertEquals(List.of("Turnus albus"), group.getAll());

    group.addRecombination("Quercus maximus albus");
    assertFalse(group.isEmpty());
    assertEquals(2, group.size());
    assertEquals(List.of("Turnus albus", "Quercus maximus albus"), group.getAll());
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1662
   */
  @Test
  public void basionymByEvidence() {
    // without evidence equally trusted names are ordered by their label
    var group = new HomotypicGroup<String>(null, "hippurus", Authorship.authors("Linnaeus"), null);
    group.addBasionym("Coryphaena hippurus", 1, "Coryphaena hippurus");
    group.addBasionym("Coriphaena hippurus", 1, "Coriphaena hippurus");
    assertEquals("Coriphaena hippurus", group.getBasionym());

    // the data says the misspelling is no original name
    HomotypicGroup.Evidence<String> evidence = new HomotypicGroup.Evidence<>() {
      @Override
      public boolean isVariant(String obj) {
        return obj.equals("Coryphaena hyppurus");
      }

      @Override
      public int penalty(String obj) {
        return obj.startsWith("Coriphaena") ? 4 : 0;
      }
    };
    group = new HomotypicGroup<>(null, "hippurus", Authorship.authors("Linnaeus"), null, evidence);
    group.addBasionym("Coriphaena hippurus", 1, "Coriphaena hippurus");
    group.addBasionym("Coryphaena hippurus", 1, "Coryphaena hippurus");
    assertEquals("Coryphaena hippurus", group.getBasionym());
    assertEquals(List.of("Coriphaena hippurus"), group.getBasionymVariations());

    // priority still beats the evidence
    group = new HomotypicGroup<>(null, "hippurus", Authorship.authors("Linnaeus"), null, evidence);
    group.addBasionym("Coryphaena hippurus", 2, "Coryphaena hippurus");
    group.addBasionym("Coriphaena hippurus", 1, "Coriphaena hippurus");
    assertEquals("Coriphaena hippurus", group.getBasionym());

    // but a known misspelling never wins, whatever its priority
    group = new HomotypicGroup<>(null, "hippurus", Authorship.authors("Linnaeus"), null, evidence);
    group.addBasionym("Coryphaena hyppurus", 1, "Coryphaena hyppurus");
    group.addBasionym("Coryphaena hippurus", 9, "Coryphaena hippurus");
    assertEquals("Coryphaena hippurus", group.getBasionym());
    group.addBasedOn("Coryphaena hyppurus", 1, "Coryphaena hyppurus");
    group.addBasedOn("Coryphaena hippurus", 9, "Coryphaena hippurus");
    assertEquals("Coryphaena hippurus", group.getBasedOn());
  }

}