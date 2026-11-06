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
 * Modified to slightly increase the similarity for strings longer than a given threshold, defaulting to 8 chars.
 */
public class ModifiedJaroWinkler implements StringSimilarity {
  
  private final float threshold = 0.7f;
  private final int boostLengthThreshold;
  
  public ModifiedJaroWinkler() {
    this.boostLengthThreshold = 8;
  }
  
  public ModifiedJaroWinkler(int boostLengthThreshold) {
    this.boostLengthThreshold = boostLengthThreshold;
  }
  
  @Override
  public double getSimilarity(String s1, String s2) {
    int[] mtp = JaroWinkler.matches(s1, s2);
    float m = (float) mtp[0];
    if (m == 0) {
      return 0;
    }
    float j = ((m / s1.length() + m / s2.length() + (m - mtp[1]) / m)) / 3;
    float jw = j < threshold ? j : j + Math.min(0.1f, 1f / mtp[3]) * mtp[2] * (1 - j);
    
    // spread the similarities close to 1 a lot more
    double sim = 100d * Math.pow(jw, 5);
    // slightly increase similarity for long strings larger than specified characters if above threshold
    return sim < threshold ? sim : (100d - (100d - sim) / Math.pow(Math.max(1, mtp[3] - boostLengthThreshold), 0.2));
  }
  
}
