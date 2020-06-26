package life.catalogue.db.mapper.legacy.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class LHigherNameTest {

  @Test
  public void stripRank() {
    assertEquals("Abies alba montana", LHigherName.stripRank("Abies alba f. montana"));
    assertEquals("Abies alba montana", LHigherName.stripRank("Abies alba var. montana"));
    assertEquals("Abies alba montana", LHigherName.stripRank("Abies alba subsp. montana"));
    assertEquals("Abies alba montana", LHigherName.stripRank("Abies alba montana"));
    assertEquals("Abies alba var", LHigherName.stripRank("Abies alba var"));
    assertEquals("Abies alba", LHigherName.stripRank("Abies alba"));
    assertEquals("Abies", LHigherName.stripRank("Abies"));
    assertEquals("A crazy virus-name L73", LHigherName.stripRank("A crazy virus-name L73"));
  }
}