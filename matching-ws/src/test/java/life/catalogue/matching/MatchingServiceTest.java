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

import life.catalogue.matching.model.NameUsageMatch;

import life.catalogue.matching.service.MatchingService;

import org.gbif.nameparser.api.Rank;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static life.catalogue.matching.service.MatchingService.normConfidence;
import static life.catalogue.matching.service.MatchingService.rankSimilarity;
import static org.gbif.nameparser.api.Rank.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    // with the introduction of the new rank class
    assertEquals(-26, rankSimilarity(FAMILY, KINGDOM));
  }

  NameUsageMatch build(String key) {
    var m = new NameUsageMatch();
    m.setUsage(new NameUsageMatch.Usage());
    m.getUsage().setKey(key);
    m.getUsage().setRank(SPECIES);
    m.getUsage().setName("Abies alba");
    return m;
  }

  @Test
  public void testMatchKeySortOrder() {
    var list = new ArrayList<>(List.of(
      build("ZHT5R"),
      build("GHT5R"),
      build("3HT5R"),
      build("KL"),
      build("CC8"),
      build("A29")
    ));
    Collections.sort(list, MatchingService.MATCH_KEY_ORDER);
    assertEquals(
      List.of("KL", "A29", "CC8", "3HT5R", "GHT5R", "ZHT5R"),
      list.stream().map(m -> m.getUsage().getKey()).collect(Collectors.toList())
    );
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
