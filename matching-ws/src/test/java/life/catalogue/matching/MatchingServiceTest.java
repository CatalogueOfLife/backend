/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.matching;

import static life.catalogue.matching.MatchingService.normConfidence;
import static life.catalogue.matching.MatchingService.rankSimilarity;
import static org.gbif.nameparser.api.Rank.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MatchingServiceTest {

  @Test
  public void rankSimilarityTest() {
    assertEquals(6, rankSimilarity(FAMILY, FAMILY));
    assertEquals(6, rankSimilarity(SPECIES, SPECIES));
    assertEquals(-1, rankSimilarity(GENUS, SUBGENUS));
    assertEquals(2, rankSimilarity(SPECIES, SPECIES_AGGREGATE));
    assertEquals(6, rankSimilarity(UNRANKED, UNRANKED));
    assertEquals(-1, rankSimilarity(UNRANKED, null));
    assertEquals(0, rankSimilarity(FAMILY, UNRANKED));
    assertEquals(0, rankSimilarity(SPECIES, UNRANKED));
    assertEquals(-9, rankSimilarity(SUBSPECIES, VARIETY));
    assertEquals(2, rankSimilarity(SUBSPECIES, INFRASPECIFIC_NAME));
    assertEquals(-35, rankSimilarity(GENUS, CLASS));
    assertEquals(-35, rankSimilarity(GENUS, FAMILY));
    // FIXME this value has changed due to rank ordinal values being changed
    // with the introduction of the new rank class
    assertEquals(-35, rankSimilarity(FAMILY, KINGDOM));
  }

  @Test
  public void testNormConfidence2() throws Exception {
    for (int x = 80; x < 150; x++) {
      System.out.println(x + " -> " + normConfidence(x));
    }
  }

  @Test
  public void testNormConfidence() throws Exception {
    assertEquals(0, normConfidence(0));
    assertEquals(0, normConfidence(-1));
    assertEquals(0, normConfidence(-10000));
    assertEquals(1, normConfidence(1));
    assertEquals(10, normConfidence(10));
    assertEquals(20, normConfidence(20));
    assertEquals(30, normConfidence(30));
    assertEquals(50, normConfidence(50));
    assertEquals(60, normConfidence(60));
    assertEquals(70, normConfidence(70));
    assertEquals(80, normConfidence(80));
    assertEquals(85, normConfidence(85));
    assertEquals(88, normConfidence(90));
    assertEquals(89, normConfidence(92));
    assertEquals(91, normConfidence(95));
    assertEquals(92, normConfidence(98));
    assertEquals(92, normConfidence(99));
    assertEquals(93, normConfidence(100));
    assertEquals(95, normConfidence(105));
    assertEquals(96, normConfidence(110));
    assertEquals(97, normConfidence(115));
    assertEquals(99, normConfidence(120));
    assertEquals(100, normConfidence(125));
    assertEquals(100, normConfidence(130));
    assertEquals(100, normConfidence(150));
    assertEquals(100, normConfidence(175));
    assertEquals(100, normConfidence(200));
    assertEquals(100, normConfidence(1000));
  }
}
