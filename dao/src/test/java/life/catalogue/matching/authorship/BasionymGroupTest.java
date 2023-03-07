package life.catalogue.matching.authorship;

import org.gbif.nameparser.api.Authorship;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class BasionymGroupTest {

  @Test
  public void getAll() {
    BasionymGroup<String> group = new BasionymGroup<>("alba", Authorship.authors("Miller"));
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

}