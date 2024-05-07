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

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.base.Joiner;
import java.io.IOException;
import javax.annotation.Nullable;
import life.catalogue.api.vocab.MatchType;
import org.gbif.nameparser.api.Rank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MatchingServiceStrictIT {

  private static MatchingService matcher;
  private static final Joiner JOINER = Joiner.on("; ").useForNull("???");

  @BeforeAll
  public static void buildMatcher() throws IOException {
    matcher =
        new MatchingService(
            MatchingTestConfiguration.provideIndex(), MatchingTestConfiguration.provideSynonyms());
  }

  private NameUsageMatch query(String name, Rank rank, Kingdom kingdom) {
    LinneanClassification cl = new NameUsageMatch();
    cl.setKingdom(kingdom.name());
    return matcher.match(name, rank, cl, true);
  }

  private void assertMatch(String name, Rank rank, Kingdom kingdom, Integer expectedKey) {
    assertMatch(name, rank, kingdom, String.valueOf(expectedKey), null);
  }

  private void assertMatch(
      String name, Rank rank, Kingdom kingdom, String expectedKey, @Nullable IntRange confidence) {
    NameUsageMatch best = query(name, rank, kingdom);

    printMatch(name, best);

    assertEquals(expectedKey, best.getUsage().getKey());
    assertNotSame(MatchType.NONE, best.getDiagnostics().getMatchType(), "Wrong match type");
    if (confidence != null) {
      assertTrue(
          confidence.contains(best.getDiagnostics().getConfidence()),
          "confidence " + best.getDiagnostics().getConfidence() + " not within " + confidence);
    }
    MatchingServiceIT.assertMatchConsistency(best);
  }

  private void assertNoMatch(String name, Rank rank, Kingdom kingdom) {
    NameUsageMatch best = query(name, rank, kingdom);

    printMatch(name, best);

    assertEquals(MatchType.NONE, best.getDiagnostics().getMatchType());
    assertNull(best.getUsage());
  }

  private void printMatch(String name, NameUsageMatch best) {
    System.out.println(
        "\n"
            + name
            + " matches "
            + (best.getUsage() != null ? best.getUsage().getName() : "no match")
            + " ["
            + (best.getUsage() != null ? best.getUsage().getKey() : "no match key")
            + "] with confidence "
            + best.getDiagnostics().getConfidence());
    if (best.getUsage() != null) {
      System.out.println(
          "  "
              + JOINER.join(
                  best.getKingdom(),
                  best.getPhylum(),
                  best.getClazz(),
                  best.getOrder(),
                  best.getFamily()));
      System.out.println("diag: " + best.getDiagnostics().getNote());
    }

    if (best.getAlternatives() != null && !best.getAlternatives().isEmpty()) {
      for (NameUsageMatch m : best.getAlternatives()) {
        System.out.println(
            "  Alt: "
                + m.getUsage().getName()
                + " ["
                + m.getUsage().getKey()
                + "] score="
                + m.getDiagnostics().getConfidence()
                + ". "
                + m.getDiagnostics().getNote());
      }
    }
  }

  @Test
  public void testMatching() throws IOException, InterruptedException {
    assertMatch("Abies", Rank.GENUS, Kingdom.PLANTAE, 2684876);

    assertNoMatch("Abies alba", Rank.SPECIES, Kingdom.ANIMALIA);
    assertNoMatch("Abies alba", Rank.GENUS, Kingdom.PLANTAE);
    assertNoMatch("Abies alba", Rank.SUBSPECIES, Kingdom.PLANTAE);

    assertMatch("Abies alba", Rank.SPECIES, Kingdom.PLANTAE, 2685484);
    assertMatch("Abies alba", Rank.SPECIES, Kingdom.PLANTAE, 2685484);
    assertMatch("Abies alba 1768", Rank.SPECIES, Kingdom.PLANTAE, 2685484);
    assertMatch("Abies alba Mill.", Rank.SPECIES, Kingdom.PLANTAE, 2685484);
    assertMatch("Abies alba Miller", Rank.SPECIES, Kingdom.PLANTAE, 2685484);
    assertMatch("Abies alba Mill., 1800", Rank.SPECIES, Kingdom.INCERTAE_SEDIS, 2685484);
    assertNoMatch("Abies alba DC", Rank.SPECIES, Kingdom.PLANTAE);
    assertNoMatch("Abies alba de Candolle, 1769", Rank.SPECIES, Kingdom.PLANTAE);
    assertNoMatch("Abies alba Linnaeus", Rank.SPECIES, Kingdom.PLANTAE);
    assertNoMatch("Abies alba L., 1989", Rank.SPECIES, Kingdom.PLANTAE);
    //    assertMatch("Abies alba nothing but a year, 1768", Rank.SPECIES, Kingdom.INCERTAE_SEDIS,
    // 2685484);
  }

  /** https://github.com/gbif/checklistbank/issues/7 */
  @Test
  public void testIssue7() throws IOException, InterruptedException {
    assertMatch("Nicotiana langsdorfii J.A. Weinm.", Rank.SPECIES, Kingdom.PLANTAE, 2928759);

    assertNoMatch("Eragrostis brownii (Kunth) Nees ex Wight", Rank.SPECIES, Kingdom.PLANTAE);
    assertMatch("Eragrostis brownii (Kunth) Nees", Rank.SPECIES, Kingdom.PLANTAE, 4149175);

    assertNoMatch("Linaria bipartita (Vent.) Desf.", Rank.SPECIES, Kingdom.PLANTAE);
    assertMatch("Linaria bipartita (Vent.) Willd.", Rank.SPECIES, Kingdom.PLANTAE, 5415006);

    assertMatch("Modiola caroliniana (L.) G. Don f.", Rank.SPECIES, Kingdom.PLANTAE, 8338793);
    assertMatch("Modiola caroliniana (L.) G. Don fil.", Rank.SPECIES, Kingdom.PLANTAE, 8338793);

    assertNoMatch("Oenothera affinis Cambess. ex A. St. Hil.", Rank.SPECIES, Kingdom.PLANTAE);
    //    assertMatch("Oenothera affinis Loud.", Rank.SPECIES, Kingdom.PLANTAE, 7860544);
    assertMatch("Oenothera affinis Camb.", Rank.SPECIES, Kingdom.PLANTAE, 3188847);

    assertMatch("Malva setigera F.K. Schimp. et Spenn.", Rank.SPECIES, Kingdom.PLANTAE, 3940638);
    assertMatch("Nicotiana langsdorfii J.A. Weinm.", Rank.SPECIES, Kingdom.PLANTAE, 2928759);
    assertMatch("Nonea lutea (Desr.) DC. ex Lam. et DC.", Rank.SPECIES, Kingdom.PLANTAE, 2926042);
    assertNoMatch("Veronica austriaca Jacq.", Rank.SPECIES, Kingdom.PLANTAE);
    assertNoMatch("Trisetaria panicea (Lam.) Maire", Rank.SPECIES, Kingdom.PLANTAE);
  }

  @Test
  public void testFilius() throws IOException, InterruptedException {
    assertMatch("Hydrocotyle ranunculoides L. f.", Rank.SPECIES, Kingdom.PLANTAE, 7978544);
    assertMatch("Hydrocotyle ranunculoides L.f.", Rank.SPECIES, Kingdom.PLANTAE, 7978544);
    assertMatch(
        "Hydrocotyle ranunculoides Linnaeus filius", Rank.SPECIES, Kingdom.PLANTAE, 7978544);
  }
}
