package life.catalogue.matching.similarity;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copyTaxon of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Arrays;

/**
 * Jaro-Winkler distance is a particularly good choice when comparing short strings of human entered data, such as names.
 * This is due to it’s relative robustness against letter transpositions and it’s weighting of similarity toward the
 * beginning of the string.
 *
 * @See <a href="http://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance">Jaro-Winkler distance on Wikipedia</a>.
 *
 * It is a variant of the Jaro distance metric which is made up of the average of three sub-calculations:
 * <ul>
 *   <li>The ratio of matching characters to the length of the first string</li>
 *   <li>The ratio of matching characters to the length of the second string</li>
 *   <li>The ratio of non-transpositions to the number matching of characters</li>
 * </ul>
 *
 */
public class JaroWinkler implements StringSimilarity {
  
  private static final float threshold = 0.7f;

  /**
   * @return the number of matching characters, half the transpositions, the length of the common prefix
   *         and the length of the longer string
   */
  static int[] matches(String s1, String s2) {
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
    // compare the matched characters of both strings in their order, walking both in step
    int transpositions = 0;
    for (int i = 0, k = 0; i < min.length(); i++) {
      if (matchIndexes[i] != -1) {
        while (!matchFlags[k]) {
          k++;
        }
        if (min.charAt(i) != max.charAt(k)) {
          transpositions++;
        }
        k++;
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

  @Override
  public double getSimilarity(String s1, String s2) {
    return similarity(s1, s2);
  }
  public static double similarity(String s1, String s2) {
    int[] mtp = matches(s1, s2);
    float m = (float) mtp[0];
    if (m == 0) {
      return 0;
    }
    float j = ((m / s1.length() + m / s2.length() + (m - mtp[1]) / m)) / 3;
    float jw = j < threshold ? j : j + Math.min(0.1f, 1f / mtp[3]) * mtp[2] * (1 - j);
    
    //(100d - (100d-s) / Math.pow(Math.max(1,l-8), 0.2))
    
    return 100d * jw;
  }
  
}
