package life.catalogue.matching.similarity;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NormalizedLevenshteinTest {

  private static final NormalizedLevenshtein SIM = new NormalizedLevenshtein();

  @Test
  public void authorshipAddition() {
    // authorship added; ~62.5
    assertTrue(SIM.getSimilarity("Abies alba", "Abies alba Mill.") >= 50.0);
  }

  @Test
  public void typo() {
    assertTrue(SIM.getSimilarity("Cus cus", "Cus cvs") >= 50.0);
  }

  @Test
  public void unrelated() {
    assertTrue(SIM.getSimilarity("Aus aus", "Zea mays") < 50.0);
  }

  @Test
  public void identical() {
    assertEquals(100.0, SIM.getSimilarity("Aus aus", "Aus aus"), 0.0);
  }
}
