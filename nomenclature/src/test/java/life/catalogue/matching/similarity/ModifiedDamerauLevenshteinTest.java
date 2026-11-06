package life.catalogue.matching.similarity;

import java.util.Random;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ModifiedDamerauLevenshteinTest {
  private static final ModifiedDamerauLevenshtein MDL1 = new ModifiedDamerauLevenshtein(1);
  private static final ModifiedDamerauLevenshtein MDL2 = new ModifiedDamerauLevenshtein(2);
  private static final ModifiedDamerauLevenshtein MDL3 = new ModifiedDamerauLevenshtein(3);

  /**
   * Distances as computed by the former implementation, which was limited to 64 characters.
   */
  @Test
  public void unchanged() {
    assertDistance(0, 0, 0, "Abies", "Abies");
    assertDistance(5, 5, 5, "", "Abies");
    assertDistance(1, 1, 1, "a", "b");
    assertDistance(1, 1, 1, "abcdef", "abdcef");
    assertDistance(4, 2, 2, "abcdef", "cdabef");
    assertDistance(8, 8, 8, "abcdefgh", "efghabcd");
    assertDistance(6, 6, 3, "abcdefghij", "abfghcdeij");
    assertDistance(1, 1, 1, "colombianum", "columbianum");
    assertDistance(2, 2, 2, "sparrmanii", "sparmani");
    assertDistance(3, 3, 3, "abcd", "dcba");
    assertDistance(5, 3, 3, "xyabcdefzz", "xycdabfezz");
    assertDistance(7, 7, 7, "tilapia sparrmanii", "chromis sparrmanni");
    assertDistance(6, 4, 4, "aabbccdd", "bbaaddcc");
  }

  /**
   * The former implementation threw an ArrayIndexOutOfBoundsException once the first string differed in 64 characters
   * or more and silently returned wrong distances for such a second string, failing the import of WoRMS Isopoda.
   */
  @Test
  public void longStrings() {
    String x = "a".repeat(100);
    String y = "b".repeat(100);
    assertDistance(100, 100, 100, x, y);
    assertDistance(100, 100, 100, x, "");
    assertDistance(30, 30, 30, x, "a".repeat(70));
    // a single edit within long strings
    String name = "Porcellio (Mesoporcellio_Sektio_Proporcellio) politus var. ubliensis " + "Verhoeff, 1901 ".repeat(10);
    assertDistance(1, 1, 1, name, name.replace("politus", "polites"));
    assertDistance(1, 1, 1, name, name.replace("politus", "poltius"));
    // two long strings differing all along
    String abc = "abcdefghij".repeat(15);
    assertDistance(150, 150, 150, abc, abc.toUpperCase());
    assertDistance(15, 15, 15, abc, abc.replace('e', 'x'));
  }

  /**
   * Long random strings must neither fail nor allocate a matrix of the product of their lengths.
   */
  @Test
  public void hugeStrings() {
    Random rnd = new Random(7);
    String x = random(rnd, 20_000, 5);
    String y = random(rnd, 20_000, 5);
    int d = MDL3.getEditDistance(x, y);
    assertEquals(d, MDL3.getEditDistance(y, x));
  }

  /**
   * 6000 random pairs below 64 characters give the very same distances as the former implementation,
   * whose results are condensed into the checksum.
   */
  @Test
  public void unchangedRandom() {
    Random rnd = new Random(42);
    long checksum = 0;
    for (var mdl : new ModifiedDamerauLevenshtein[]{MDL1, MDL2, MDL3}) {
      for (int i = 0; i < 2000; i++) {
        String x = random(rnd, 2 + rnd.nextInt(40), 4);
        String y = mutate(rnd, x);
        checksum = checksum * 31 + mdl.getEditDistance(x, y);
      }
    }
    assertEquals(-5587272050699560452L, checksum);
  }

  private static void assertDistance(int d1, int d2, int d3, String x, String y) {
    assertEquals(d1, MDL1.getEditDistance(x, y));
    assertEquals(d1, MDL1.getEditDistance(y, x));
    assertEquals(d2, MDL2.getEditDistance(x, y));
    assertEquals(d2, MDL2.getEditDistance(y, x));
    assertEquals(d3, MDL3.getEditDistance(x, y));
    assertEquals(d3, MDL3.getEditDistance(y, x));
  }

  private static String random(Random rnd, int length, int letters) {
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append((char) ('a' + rnd.nextInt(letters)));
    }
    return sb.toString();
  }

  /**
   * The very mutations the checksum of {@link #unchangedRandom()} was recorded with.
   */
  private static String mutate(Random rnd, String x) {
    StringBuilder sb = new StringBuilder(x);
    int edits = rnd.nextInt(6);
    for (int i = 0; i < edits; i++) {
      int p = rnd.nextInt(sb.length());
      switch (rnd.nextInt(4)) {
        case 0 -> sb.deleteCharAt(p);
        case 1 -> sb.insert(p, (char) ('a' + rnd.nextInt(4)));
        case 2 -> sb.setCharAt(p, (char) ('a' + rnd.nextInt(4)));
        default -> {
          if (p + 1 < sb.length()) {
            char c = sb.charAt(p);
            sb.setCharAt(p, sb.charAt(p + 1));
            sb.setCharAt(p + 1, c);
          }
        }
      }
      if (sb.length() < 2) {
        sb.append('a');
      }
    }
    return sb.toString();
  }
}
