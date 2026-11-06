package life.catalogue.matching.similarity;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares the transposition count of {@link JaroWinkler}, which no longer copies the matched characters into
 * arrays, with the code it replaced, and checks that {@link ModifiedJaroWinkler} still scores the same now that it
 * shares that code.
 */
public class JaroWinklerEquivalenceTest {

  private static int[] oldMatches(String s1, String s2) {
    String max, min;
    if (s1.length() > s2.length()) {
      max = s1;
      min = s2;
    } else {
      max = s2;
      min = s1;
    }
    int range = Math.max(max.length() / 2 - 1, 0);
    int[] matchIndexes = new int[min.length()];
    Arrays.fill(matchIndexes, -1);
    boolean[] matchFlags = new boolean[max.length()];
    int matches = 0;
    for (int mi = 0; mi < min.length(); mi++) {
      char c1 = min.charAt(mi);
      for (int xi = Math.max(mi - range, 0), xn = Math.min(mi + range + 1, max.length()); xi < xn; xi++) {
        if (!matchFlags[xi] && c1 == max.charAt(xi)) {
          matchIndexes[mi] = xi;
          matchFlags[xi] = true;
          matches++;
          break;
        }
      }
    }
    char[] ms1 = new char[matches];
    char[] ms2 = new char[matches];
    for (int i = 0, si = 0; i < min.length(); i++) {
      if (matchIndexes[i] != -1) {
        ms1[si] = min.charAt(i);
        si++;
      }
    }
    for (int i = 0, si = 0; i < max.length(); i++) {
      if (matchFlags[i]) {
        ms2[si] = max.charAt(i);
        si++;
      }
    }
    int transpositions = 0;
    for (int mi = 0; mi < ms1.length; mi++) {
      if (ms1[mi] != ms2[mi]) {
        transpositions++;
      }
    }
    int prefix = 0;
    for (int mi = 0; mi < min.length(); mi++) {
      if (s1.charAt(mi) == s2.charAt(mi)) {
        prefix++;
      } else {
        break;
      }
    }
    return new int[]{matches, transpositions / 2, prefix, max.length()};
  }

  private static double oldModifiedSimilarity(String s1, String s2, int boostLengthThreshold) {
    int[] mtp = oldMatches(s1, s2);
    float m = (float) mtp[0];
    if (m == 0) {
      return 0;
    }
    float j = ((m / s1.length() + m / s2.length() + (m - mtp[1]) / m)) / 3;
    float jw = j < 0.7f ? j : j + Math.min(0.1f, 1f / mtp[3]) * mtp[2] * (1 - j);
    double sim = 100d * Math.pow(jw, 5);
    return sim < 0.7f ? sim : (100d - (100d - sim) / Math.pow(Math.max(1, mtp[3] - boostLengthThreshold), 0.2));
  }

  @Test
  public void sameResults() {
    final Random rnd = new Random(20261106);
    final String alphabet = "abcdeaei";
    final ModifiedJaroWinkler mjw = new ModifiedJaroWinkler();
    for (int i = 0; i < 200_000; i++) {
      String s1 = random(rnd, alphabet, 12);
      String s2 = rnd.nextInt(5) == 0 ? shuffle(rnd, s1) : random(rnd, alphabet, 12);
      assertArrayEquals(oldMatches(s1, s2), JaroWinkler.matches(s1, s2), s1 + " vs " + s2);
      assertEquals(oldModifiedSimilarity(s1, s2, 8), mjw.getSimilarity(s1, s2), s1 + " vs " + s2);
    }
  }

  private static String random(Random rnd, String alphabet, int maxLen) {
    StringBuilder sb = new StringBuilder();
    int len = rnd.nextInt(maxLen + 1);
    for (int i = 0; i < len; i++) {
      sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
    }
    return sb.toString();
  }

  private static String shuffle(Random rnd, String x) {
    char[] chars = x.toCharArray();
    for (int i = chars.length - 1; i > 0; i--) {
      int j = rnd.nextInt(i + 1);
      char tmp = chars[i];
      chars[i] = chars[j];
      chars[j] = tmp;
    }
    return new String(chars);
  }
}
