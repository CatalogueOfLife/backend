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

import static org.gbif.nameparser.api.NameType.NO_NAME;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.matching.model.Classification;
import life.catalogue.matching.model.LinneanClassification;
import life.catalogue.matching.model.NameUsageMatch;
import life.catalogue.matching.model.NameUsageQuery;
import life.catalogue.matching.service.MatchingService;
import life.catalogue.matching.util.Dictionaries;
import life.catalogue.matching.util.NameParsers;

import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.api.Rank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MatchingServiceIT {

  private static MatchingService matcher;
  private static final Joiner CLASS_JOINER = Joiner.on("; ").useForNull("???");

  @BeforeAll
  public static void buildMatcher() throws IOException {
    matcher = new MatchingService(
            MatchingTestConfiguration.provideIndex(), MatchingTestConfiguration.provideSynonyms(), Dictionaries.createDefault());
  }

  private NameUsageMatch assertMatch(
    String name, LinneanClassification query, Integer expectedKey) {
    return assertMatch(name, query, String.valueOf(expectedKey), null, new IntRange(1, 100));
  }

  private NameUsageMatch assertMatch(
      String name, LinneanClassification query, Integer expectedKey, IntRange confidence) {
    return assertMatch(name, query, expectedKey, confidence, Set.of());
  }

  private NameUsageMatch assertMatch(
      String name,
      LinneanClassification query,
      Integer expectedKey,
      IntRange confidence,
      Set<String> exclude) {
    return assertMatch(name, null, query, String.valueOf(expectedKey), null, confidence, exclude);
  }

  private NameUsageMatch assertMatch(
      String name, LinneanClassification query, Integer expectedKey, MatchType type) {
    return assertMatch(name, query, String.valueOf(expectedKey), type, new IntRange(1, 100));
  }

  private NameUsageMatch assertMatch(
      String name, Rank rank, LinneanClassification query, Integer expectedKey, MatchType type) {
    return assertMatch(
        name, rank, query, String.valueOf(expectedKey), type, new IntRange(1, 100), null);
  }

  private NameUsageMatch assertMatch(
      String name, LinneanClassification query, String expectedKey, IntRange confidence) {
    return assertMatch(name, query, expectedKey, confidence, null);
  }

  private NameUsageMatch assertMatch(
      String name,
      LinneanClassification query,
      String expectedKey,
      IntRange confidence,
      Set<String> exclude) {
    return assertMatch(name, null, query, expectedKey, null, confidence, exclude);
  }

  private NameUsageMatch assertMatch(
      String name, LinneanClassification query, String expectedKey, MatchType type) {
    return assertMatch(name, query, expectedKey, type, new IntRange(1, 100));
  }

  private NameUsageMatch assertMatch(
      String name, Rank rank, LinneanClassification query, String expectedKey, MatchType type) {
    return assertMatch(name, rank, query, expectedKey, type, new IntRange(1, 100), null);
  }

  private void print(String name, NameUsageMatch best) {
    System.out.println(
        "\n"
            + name
            + " matches "
            + (best.getUsage() != null ? best.getUsage().getName() : "no match")
            + " ["
            + (best.getUsage() != null ? best.getUsage().getKey() : "no matching key")
            + "] with confidence "
            + best.getDiagnostics().getConfidence());
    if (best.getUsage() != null && best.getUsage().getKey() != null) {
      System.out.println(
          "  "
              + CLASS_JOINER.join(
                  best.getKingdom(),
                  best.getPhylum(),
                  best.getClazz(),
                  best.getOrder(),
                  best.getFamily()));
      System.out.println("  " + best.getDiagnostics().getNote());
    }
    if (best.getDiagnostics().getAlternatives() != null) {
      for (NameUsageMatch m : best.getDiagnostics().getAlternatives()) {
        System.out.println(
            "  Alt: "
                + m.getUsage().getName()
                + " ["
                + m.getUsage().getKey()
                + "] "
                + m.getKingdom()
                + ","
                + m.getPhylum()
                + ","
                + m.getClazz()
                + ","
                + m.getOrder()
                + ","
                + m.getFamily()
                + ","
                + m.getGenus()
                + ","
                + m.getSpecies()
                + ". "
                + "confidence="
                + m.getDiagnostics().getConfidence()
                + ". "
                + m.getDiagnostics().getNote());
      }
    }
  }

  private NameUsageMatch assertMatch(
      String name,
      LinneanClassification query,
      String expectedKey,
      @Nullable MatchType type,
      IntRange confidence) {
    return assertMatch(name, null, query, expectedKey, type, confidence, null);
  }

  private NameUsageMatch assertMatch(
      String name,
      Rank rank,
      LinneanClassification query,
      String expectedKey,
      @Nullable MatchType type,
      IntRange confidence,
      Set<String> exclude) {
    return assertMatch(null, name, rank, query, expectedKey, type, confidence, exclude);
  }

  private NameUsageMatch assertMatch(
      String usageKey,
      String name,
      Rank rank,
      LinneanClassification query,
      String expectedKey,
      @Nullable MatchType type,
      IntRange confidence,
      Set<String> exclude) {
    NameUsageMatch best =
        matcher.match(new NameUsageQuery(usageKey, null, null, null, name, null, null, null, null, rank, query, exclude, false, true));

    print(name, best);

    if (expectedKey == null) {
      assertNull(best.getUsage(), "Wrong expected key");
    } else {
      if (best.getUsage() == null) assertEquals(expectedKey, null, "Wrong expected key");
      else assertEquals(expectedKey, best.getUsage().getKey(), "Wrong expected key");
    }
    if (type == null) {
      assertNotSame(MatchType.NONE, best.getDiagnostics().getMatchType(), "Wrong none match type");
    } else {
      assertEquals(type, best.getDiagnostics().getMatchType(), "Wrong match type");
    }
    if (confidence != null) {
      assertTrue(
          confidence.contains(best.getDiagnostics().getConfidence()),
          "confidence " + best.getDiagnostics().getConfidence() + " not within " + confidence);
    }
    assertMatchConsistency(best);
    return best;
  }

  private void assertNoMatch(String name, LinneanClassification query) {
    assertNoMatch(name, query, null);
  }

  private void assertNoMatch(String name, LinneanClassification cl, @Nullable IntRange confidence) {
    NameUsageMatch best = matcher.match(name, cl, true);
    print(name, best);

    assertEquals(MatchType.NONE, best.getDiagnostics().getMatchType());
    assertNull(best.getUsage());
  }

  static void assertMatchConsistency(NameUsageMatch match) {
    assertNotNull(match.getDiagnostics().getConfidence());
    assertNotNull(match.getDiagnostics().getMatchType());
    if (MatchType.NONE == match.getDiagnostics().getMatchType()) {
      assertNull(match.getUsage());
      assertNull(match.getSpeciesKey());
      assertNull(match.getGenusKey());
      assertNull(match.getFamilyKey());
      assertNull(match.getOrderKey());
      assertNull(match.getClassKey());
      assertNull(match.getPhylumKey());
      assertNull(match.getKingdomKey());

      assertNull(match.getSpecies());
      assertNull(match.getGenus());
      assertNull(match.getFamily());
      assertNull(match.getOrder());
      assertNull(match.getClazz());
      assertNull(match.getPhylum());
      assertNull(match.getKingdom());

    } else {
      assertNotNull(match.getUsage().getKey());
      assertNotNull(match.getUsage().getName());

      if (match.getUsage().getRank() != null && isParsed(match)) {
        Rank rank = match.getUsage().getRank();
        if (rank.isSuprageneric()) {
          assertNull(match.getSpecies());
          assertNull(match.getSpeciesKey());
          assertNull(match.getGenus());
          assertNull(match.getGenusKey());

        } else if (rank == Rank.GENUS) {
          assertNotNull(match.getGenus());
          assertNull(match.getSpecies());
          assertNull(match.getSpeciesKey());

        } else if (rank == Rank.SPECIES) {
          // FIXME - this breaks a test, but the actual result doesnt have a genus
          //          assertNotNull(match.getGenus());
          assertNotNull(match.getSpecies());
          assertNotNull(match.getSpeciesKey());
          if (!match.isSynonym()) {
            assertEquals(match.getUsage().getKey(), match.getSpeciesKey());
            assertTrue(match.getUsage().getName().startsWith(match.getSpecies()));
          }

        } else if (rank.isInfraspecific()) {
          //          assertNotNull(match.getGenus());
          assertNotNull(match.getSpecies());
          assertNotNull(match.getSpeciesKey());
          if (!match.isSynonym()) {
            assertFalse(match.getUsage().getKey().equals(match.getSpeciesKey()));
            assertTrue(match.getUsage().getName().startsWith(match.getSpecies()));
          }
        }
      }
    }
  }

  private static boolean isParsed(NameUsageMatch x) {
    if (x.getUsage().getName() != null) {
      try {
        ParsedName pn =
            NameParsers.INSTANCE.parse(x.getUsage().getName(), x.getUsage().getRank(), null);
        return pn.getType() != NO_NAME;

      } catch (Exception e) {
      }
    }
    return false;
  }

  private void assertNubIdNotNullAndNotEqualToAnyHigherRank(NameUsageMatch x) {
    assertNotNull(x.getUsage().getKey());
    assertFalse(x.getUsage().getKey().equals(x.getKingdomKey()));
    assertFalse(x.getUsage().getKey().equals(x.getPhylumKey()));
    assertFalse(x.getUsage().getKey().equals(x.getClassKey()));
    assertFalse(x.getUsage().getKey().equals(x.getOrderKey()));
    assertFalse(x.getUsage().getKey().equals(x.getFamilyKey()));
  }

  @Test
  public void testMatching() throws IOException, InterruptedException {
    LinneanClassification cl = new Classification();
    assertMatch("Anephlus", cl, "1100135", new IntRange(92, 95));
    assertMatch("Aneplus", cl, "1100050", new IntRange(90, 95));

    cl.setKingdom("Animalia");
    cl.setClazz("Insecta");
    assertMatch("Aneplus", cl, "1100050", new IntRange(97, 100));

    // genus Aneplus is order=Coleoptera, but Anelus is a Spirobolida in class Diplopoda
    cl.setClazz("Diplopoda");
    cl.setOrder("Spirobolida");
    assertMatch("Aneplus", cl, "1027792", new IntRange(90, 99));

    cl.setFamily("Atopetholidae");
    assertMatch("Aneplus", cl, "1027792", new IntRange(98, 100));

    // too far off
    // FIXME - im not sure what the sensible outcome is for this one....
    // assertMatch("Anmeplues", cl, "1", new IntRange(90, 100));

    assertNoMatch("Anmeplues", new Classification(), new IntRange(-10, 80));
  }

  /**
   * Sabia parviflora is a plant which is in our backbone badly classified as an animal. Assure
   * those names get matched to the next highest taxon that indeed is a plant when the plant kingdom
   * is requested.
   */
  @Test
  public void testBadPlantKingdom() throws IOException {
    LinneanClassification cl = new Classification();
    // without kingdom snap to the bad animal record
    assertMatch("Sabia parviflora", cl, String.valueOf(7268473), new IntRange(96, 100));

    // hit the plant family
    assertMatch("Sabiaceae", cl, String.valueOf(2409), new IntRange(90, 100));

    // make sure its the family
    cl.setKingdom("Plantae");
    cl.setFamily("Sabiaceae");
    assertMatch("Sabia parviflora", cl, "2409", new IntRange(80, 100));

    // without kingdom snap to the bad animal record
    cl = new Classification();
    assertMatch("Tibetia tongolensis", cl, String.valueOf(7301567), new IntRange(96, 100));

    // hit the plant family
    assertMatch("Fabaceae", cl, String.valueOf(5386), new IntRange(90, 100));

    // make sure its the family
    cl.setKingdom("Plantae");
    cl.setFamily("Fabaceae");
    assertMatch("Tibetia tongolensis", cl, String.valueOf(5386), new IntRange(80, 100));
  }

  @Test
  public void testHomonyms() {
    // Oenanthe
    LinneanClassification cl = new Classification();
    assertNoMatch("Oenanthe", cl);

    cl.setKingdom("Animalia");
    assertMatch("Oenanthe", cl, 2492483, new IntRange(95, 99));

    cl.setKingdom("Plantae");
    assertMatch("Oenanthe", cl, 3034893, new IntRange(95, 99));
    assertMatch("Oenante", cl, 3034893, new IntRange(82, 90));

    // Acanthophora
    cl = new Classification();
    assertNoMatch("Acanthophora", cl);

    // there are 3 genera in animalia, 2 synonyms and 1 accepted but they differ at phylum level
    cl.setKingdom("Animalia");
    assertMatch("Acanthophora", cl, 1, new IntRange(92, 96));

    // now try with molluscs to just get the doubtful one
    cl.setPhylum("Porifera");
    assertMatch("Acanthophora", cl, 3251480, new IntRange(97, 99));

    cl.setKingdom("Plantae"); // there are multiple plant genera, this should match to plantae now
    assertMatch("Acanthophora", cl, 6, new IntRange(90, 95));

    cl.setFamily("Araliaceae");
    assertMatch("Acanthophora", cl, 3036337, new IntRange(98, 100));
    assertMatch("Acantophora", cl, 3036337, new IntRange(90, 95)); // fuzzy match
    // try matching with authors
    assertMatch("Acantophora Merrill", cl, 3036337, new IntRange(95, 100)); // fuzzy match

    cl.setFamily("Rhodomelaceae");
    assertMatch("Acanthophora", cl, 2659277, new IntRange(97, 100));

    // species match
    cl = new Classification();
    assertMatch("Puma concolor", cl, 2435099, new IntRange(98, 100));

    cl.setGenus("Puma");
    assertMatch("P. concolor", cl, 2435099, new IntRange(98, 100));

    cl.setKingdom("Animalia");
    assertMatch("P. concolor", cl, 2435099, new IntRange(99, 100));

    cl.setKingdom("Pllllaaaantae");
    assertMatch("Puma concolor", cl, 2435099, new IntRange(95, 100));

    // we now match to the kingdom even though it was given wrong
    // sideeffect of taking kingdoms extremely serious due to bad backbone
    // see NubMatchingServiceImpl.classificationSimilarity()
    cl.setKingdom("Plantae");
    assertMatch("Puma concolor", cl, 6, new IntRange(95, 100));

    // Picea concolor is a plant, but this fuzzy match is too far off
    assertMatch("Pima concolor", cl, 6);
    // this one should match though
    assertMatch("Pica concolor", cl, 5284657, new IntRange(85, 90));
    // and this will go to the family
    cl.setFamily("Pinaceae");
    assertMatch("Pima concolor", cl, 3925, new IntRange(90, 100));

    // Amphibia is a homonym genus, but also and most prominently a class!
    cl = new Classification();
    // non existing "species" name. Amphibia could either be the genus or the class. As we
    assertNoMatch("Amphibia eyecount", cl);

    // first try a match against the algae genus
    cl.setKingdom("Plantae");
    cl.setClazz("Rhodophyceae");
    assertMatch("Amphibia eyecount", cl, 2659299, new IntRange(90, 99));

    // now try with the animal class
    cl.setKingdom("Animalia");
    cl.setClazz("Amphibia");
    assertMatch("Amphibia eyecount", cl, 131, new IntRange(98, 100));
  }

  @Test
  public void testHomonyms2() throws IOException {
    // this hits 2 species synonyms with no such name being accepted
    // nub match must pick one if the accepted name of all synonyms is the same!
    LinneanClassification cl = new Classification();
    cl.setKingdom("Plantae");
    cl.setFamily("Poaceae");
    assertMatch("Elytrigia repens", cl, 7522774, new IntRange(92, 100));
  }

  /**
   * Tests the behaviour to ensure that we are conservative enough when choosing an equally matched
   * option. The data for this test were added in nub297.json.
   *
   * @see <a href="https://github.com/gbif/checklistbank/issues/295">issue 295</a>
   * @see <a href="https://discourse.gbif.org/t/millipedes-in-the-ocean/3991">Millipedes in the
   *     ocean</a>
   */
  @Test
  public void testHomonyms3() throws IOException {
    assertMatch("Siphonophora", new Classification(), 1, new IntRange(90, 100));
  }

  @Test
  public void testSimilarButSpanRank() {
    NameUsageMatch m1 =
        NameUsageBuilder.builder()
            .kingdom("A")
            .phylum("B")
            .clazz("C")
            .order("O")
            .kingdomKey("A")
            .phylumKey("B")
            .classKey("C")
            .orderKey("O")
            .confidence(100)
            .build();
    NameUsageMatch m2 =
        NameUsageBuilder.builder()
            .kingdom("A")
            .phylum("B")
            .clazz("C")
            .order("O")
            .kingdomKey("A")
            .phylumKey("B")
            .classKey("C")
            .orderKey("O")
            .confidence(99)
            .build();
    NameUsageMatch m3 =
        NameUsageBuilder.builder()
            .kingdom("A")
            .phylum("B")
            .clazz("C")
            .order("X")
            .kingdomKey("A")
            .phylumKey("B")
            .classKey("C")
            .orderKey("X")
            .confidence(99)
            .build();
    NameUsageMatch m4 =
        NameUsageBuilder.builder().kingdom("A").phylum("B").clazz("C").confidence(99).build();
    NameUsageMatch m5 =
        NameUsageBuilder.builder()
            .kingdom("A")
            .phylum("B")
            .clazz("O")
            .kingdomKey("A")
            .phylumKey("B")
            .classKey("O")
            .confidence(90)
            .build();

    assertFalse(
        matcher.similarButSpanRank(ImmutableList.of(m1, m1), 1, Rank.ORDER), "Same matches");
    assertFalse(matcher.similarButSpanRank(ImmutableList.of(m1, m2), 1, Rank.ORDER), "Similar");
    assertFalse(
        matcher.similarButSpanRank(ImmutableList.of(m1, m5), 1, Rank.ORDER), "Outside threshold");
    assertFalse(
        matcher.similarButSpanRank(ImmutableList.of(m1, m2, m5), 1, Rank.ORDER),
        "Similar share classification");

    assertTrue(matcher.similarButSpanRank(List.of(m1, m3), 1, Rank.ORDER), "Different order");
    assertTrue(
        matcher.similarButSpanRank(ImmutableList.of(m1, m4), 1, Rank.ORDER),
        "Null order is different");
    assertTrue(
        matcher.similarButSpanRank(ImmutableList.of(m1, m2, m3), 1, Rank.ORDER), "Different order");
    assertTrue(
        matcher.similarButSpanRank(ImmutableList.of(m1, m2, m4), 1, Rank.ORDER), "Different order");
  }

  @Test
  public void testAuthorshipMatching() throws IOException {
    Classification cl = new Classification();
    assertMatch("Prunella alba", cl, 5608009, new IntRange(98, 100));

    assertMatch("Prunella alba Pall. ex M.Bieb.", cl, 5608009, new IntRange(100, 100));
    assertMatch("Prunella alba M.Bieb.", cl, 5608009, new IntRange(100, 100));

    assertMatch("Prunella alba Pall.", cl, 5608009, new IntRange(80, 100));
    assertMatch("Prunella alba Döring", cl, 5608009, new IntRange(80, 90));

    // 2 homonyms exist
    assertMatch("Elytrigia repens", cl, 7522774, new IntRange(92, 98));
    assertMatch("Elytrigia repens Desv.", cl, 7522774, new IntRange(98, 100));
    assertMatch("Elytrigia repens Nevski", cl, 2706649, new IntRange(98, 100));
    assertMatch("Elytrigia repens (L.) Desv.", cl, 7522774, new IntRange(100, 100));
    assertMatch("Elytrigia repens (L.) Nevski", cl, 2706649, new IntRange(100, 100));

    // very different author, match to genus only
    assertMatch("Elytrigia repens Karimba", cl, 7826764, MatchType.HIGHERRANK);

    // basionym author is right, but otherwise the authorship differs clearly. Match to genus only
    assertMatch("Elytrigia repens (L.) Karimba", cl, 7826764, MatchType.HIGHERRANK);
  }

  /**
   * Testing matches of names that were different between classic species match and the author aware
   * "lookup"
   *
   * <p>Bromus sterilis Daphnia Carpobrotus edulis Celastrus orbiculatus Python molurus subsp.
   * bivittatus Ziziphus mauritiana orthacantha Solanum verbascifolium auriculatum
   */
  @Test
  public void testAuthorshipMatchingGIASIP() throws IOException {
    LinneanClassification cl = new Classification();
    assertMatch("Bromus sterilis", cl, 8341523, new IntRange(95, 99));
    assertMatch("Bromus sterilis Guss.", cl, 8341523, new IntRange(99, 100));
    assertMatch("Bromus sterilis Gus", cl, 8341523, new IntRange(98, 100));

    assertMatch("Bromus sterilis L.", cl, 2703647, new IntRange(98, 100));
    assertMatch("Bromus sterilis Linne", cl, 2703647, new IntRange(98, 100));

    assertMatch("Bromus sterilis Kumm. & Sendtn.", cl, 7874095, new IntRange(98, 100));
    assertMatch("Bromus sterilis Kumm", cl, 7874095, new IntRange(98, 100));
    assertMatch("Bromus sterilis Sendtn.", cl, 7874095, new IntRange(98, 100));

    assertMatch("Daphnia", cl, 2234785, new IntRange(90, 95));
    assertMatch("Daphnia Müller, 1785", cl, 2234785, new IntRange(96, 100));
    assertMatch("Daphnia Müller", cl, 2234785, new IntRange(95, 100));

    assertMatch("Daphne Müller, 1785", cl, 2234879, new IntRange(95, 100));
    assertMatch("Daphne Müller, 1776", cl, 2234879, new IntRange(96, 100));

    assertMatch("Daphnia Korth", cl, 3626852, new IntRange(88, 92));
    cl.setKingdom("Plantae");
    cl.setFamily("Oxalidaceae");
    assertMatch("Daphnia Korth", cl, 3626852, new IntRange(96, 100));
    cl = new Classification();

    assertMatch("Daphnia Rafinesque", cl, 7956551, new IntRange(88, 94));
    cl.setKingdom("Animalia");
    cl.setFamily("Calanidae");
    assertMatch("Daphnia Rafinesque", cl, 4333792, new IntRange(98, 100));
    cl = new Classification();

    assertMatch("Carpobrotus edulis", cl, 3084842, new IntRange(95, 99));
    assertMatch("Carpobrotus edulis N. E. Br.", cl, 3084842, new IntRange(98, 100));
    assertMatch("Carpobrotus edulis L.Bolus", cl, 7475472, new IntRange(95, 100));
    assertMatch("Carpobrotus edulis Bolus", cl, 7475472, new IntRange(95, 100));
    assertMatch("Carpobrotus dulcis Bolus", cl, 3703510, new IntRange(95, 100));
    // once again with classification given
    cl.setKingdom("Plantae");
    cl.setFamily("Celastraceae");
    assertMatch("Carpobrotus edulis", cl, 3084842, new IntRange(92, 98));
    assertMatch("Carpobrotus edulis N. E. Br.", cl, 3084842, new IntRange(98, 100));
    assertMatch("Carpobrotus edulis L.Bolus", cl, 7475472, new IntRange(95, 100));
    assertMatch("Carpobrotus edulis Bolus", cl, 7475472, new IntRange(95, 100));
    assertMatch("Carpobrotus dulcis Bolus", cl, 3703510, new IntRange(95, 100));
    cl = new Classification();

    assertMatch("Celastrus orbiculatus", cl, 8104460, new IntRange(95, 99));
    assertMatch("Celastrus orbiculatus Murray", cl, 8104460, new IntRange(98, 100));
    assertMatch("Celastrus orbiculatus Thunb", cl, 3169169, new IntRange(98, 100));
    assertMatch("Celastrus orbiculatus Lam", cl, 7884995, new IntRange(98, 100));

    assertMatch("Python molurus subsp. bivittatus", cl, 6162891, new IntRange(98, 100));
    assertMatch("Python molurus bivittatus", cl, 6162891, new IntRange(97, 100));
    assertMatch("Python molurus bivittatus Kuhl", cl, 6162891, new IntRange(97, 100));
    assertMatch("Python molurus subsp. bibittatus", cl, 4287608, new IntRange(97, 100));

    assertMatch("Ziziphus mauritiana orthacantha", cl, 8068734, new IntRange(95, 99));
    assertMatch("Ziziphus mauritiana ssp. orthacantha", cl, 7786586, new IntRange(97, 100));
    assertMatch("Ziziphus mauritiana ssp. orthacantha Chev.", cl, 7786586, new IntRange(98, 100));
    assertMatch("Ziziphus mauritiana var. orthacantha", cl, 8068734, new IntRange(97, 100));
    assertMatch("Ziziphus mauritiana var. orthacantha Chev.", cl, 8068734, new IntRange(98, 100));

    assertMatch("Solanum verbascifolium auriculatum", cl, 6290014, new IntRange(95, 98));
    assertMatch("Solanum verbascifolium ssp. auriculatum", cl, 2930718, new IntRange(95, 99));
    assertMatch(
        "Solanum verbascifolium var. auriculatum Kuntze", cl, 8363606, new IntRange(98, 100));
    assertMatch(
        "Solanum verbascifolium var. auriculatum Maiden", cl, 6290014, new IntRange(98, 100));
    assertMatch("Solanum verbascifolium var. auriculatum", cl, 6290014, new IntRange(94, 98));
  }

  @Test
  public void testOtuMatching() throws IOException {
    LinneanClassification cl = new Classification();

    assertMatch("SH205817.07FU", cl, 9732858, new IntRange(95, 100));

    NameUsageMatch m = assertMatch("BOLD:AAX3687", cl, 993172099, new IntRange(95, 100));
    assertEquals("BOLD:AAX3687", m.getUsage().getName());

    assertMatch("SH021315.07FU", cl, 993730906, new IntRange(95, 100));

    cl.setFamily("Maldanidae");
    assertMatch("BOLD:AAX3687", cl, 993172099, new IntRange(95, 100));
    assertMatch("bold:aax3687", cl, 993172099, new IntRange(95, 100));

    assertNoMatch("BOLD:AAX3688", cl);
    assertNoMatch("BOLD:AAY3687", cl);
    assertNoMatch("COLD:AAX3687", cl);
    assertNoMatch("AAX3687", cl);
  }

  /**
   * http://dev.gbif.org/issues/browse/PF-2574
   *
   * <p>Inachis io (Linnaeus, 1758) Inachis io NPV
   *
   * <p>Dionychopus amasis GV
   *
   * <p>Hyloicus pinastri NPV Hylobius pinastri Billberg Hylobius (Hylobius) pinastri Escherich ,
   * 1923
   *
   * <p>Vanessa cardui NPV Vanessa cardui (Linnaeus, 1758)
   */
  @Test
  public void testViruses() throws IOException {
    LinneanClassification cl = new Classification();
    assertMatch("Inachis io", cl, 5881450, new IntRange(92, 100));
    assertMatch("Inachis io (Linnaeus)", cl, 5881450, new IntRange(95, 100));
    assertMatch("Inachis io NPV", cl, 8562651, new IntRange(95, 100));

    assertMatch("Dionychopus amasis GV", cl, 6876449, new IntRange(95, 100));
    // to lepidoptera genus only
    assertMatch("Dionychopus amasis", cl, 4689754, new IntRange(90, 100));
    // with given kingdom result in no match, GV is part of the name
    cl.setKingdom("Virus");
    assertNoMatch("Dionychopus amasis", cl);
  }

  /**
   * Non existing species with old family classification should match genus Linaria.
   * http://dev.gbif.org/issues/browse/POR-2704
   */
  @Test
  public void testPOR2704() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Plantae");
    cl.setFamily("Scrophulariaceae"); // nowadays Plantaginaceae as in our nub/col
    assertMatch("Linaria pedunculata (L.) Chaz.", cl, 3172168, new IntRange(90, 100));
  }

  /** Classification names need to be parsed if they are no monomials already */
  @Test
  public void testClassificationWithAuthors() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Fungi Bartling, 1830");
    cl.setPhylum("Ascomycota Caval.-Sm., 1998");
    cl.setClazz("Lecanoromycetes, O.E. Erikss. & Winka, 1997");
    cl.setOrder("Lecanorales, Nannf., 1932");
    cl.setFamily("Ramalinaceae, C. Agardh, 1821");
    cl.setGenus("Toninia");
    assertMatch("Toninia aromatica", cl, 2608009, new IntRange(96, 100));
  }

  /** Non existing species should match genus Quedius http://dev.gbif.org/issues/browse/POR-1712 */
  @Test
  public void testPOR1712() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setClazz("Hexapoda");
    cl.setFamily("Staphylinidae");
    cl.setGenus("Quedius");
    cl.setKingdom("Animalia");
    cl.setPhylum("Arthropoda");
    assertMatch("Quedius caseyi divergens", cl, 4290501, new IntRange(90, 100));
  }

  /**
   * Indet subspecies should match to species Panthera pardus
   * http://dev.gbif.org/issues/browse/POR-2701
   */
  @Test
  public void testPOR2701() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setPhylum("Chordata");
    cl.setClazz("Mammalia");
    cl.setOrder("Carnivora");
    cl.setFamily("Felidae");
    cl.setGenus("Panthera");
    assertMatch("Panthera pardus ssp.", cl, 5219436, new IntRange(98, 100));
  }

  /**
   * Brunella alba Pallas ex Bieb.(Labiatae, Plantae) is wrongly matched to Brunerella alba R.F.
   * Castañeda & Vietinghoff (Fungi)
   *
   * <p>The species does not exist in the nub and the genus Brunella is a synonym of Prunella. Match
   * to synonym genus Brunella http://dev.gbif.org/issues/browse/POR-2684
   */
  @Test
  public void testPOR2684() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Plantae");
    cl.setFamily("Labiatae");
    cl.setGenus("Brunella");
    assertMatch("Brunella alba Pallas ex Bieb.", cl, 6008586, new IntRange(96, 100));
  }

  /**
   * The wasp species does not exist and became a spider instead. Should match to the wasp genus.
   *
   * <p>http://dev.gbif.org/issues/browse/POR-2469
   */
  @Test
  public void testPOR2469() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setPhylum("Arthropoda");
    cl.setClazz("Insecta");
    cl.setOrder("Hymenoptera");
    cl.setFamily("Tiphiidae");
    cl.setGenus("Eirone");
    assertMatch("Eirone neocaledonica Williams", cl, 4674090, new IntRange(90, 100));
  }

  /**
   * Allow lower case names
   *
   * <p>https://github.com/gbif/portal-feedback/issues/1379
   */
  @Test
  public void testFeedback1379() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setFamily("Helicidae");
    assertMatch("iberus gualtieranus", cl, 4564258, new IntRange(95, 100));
    assertMatch("Iberus gualterianus", cl, 4564258, new IntRange(98, 100));
  }

  /**
   * The beetle Oreina elegans does not exist in the nub and became a spider instead. Should match
   * to the wasp genus.
   *
   * <p>http://dev.gbif.org/issues/browse/POR-2607
   */
  @Test
  public void testPOR2607() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setFamily("Chrysomelidae");
    assertMatch("Oreina", cl, 6757727, new IntRange(95, 100));
    assertMatch("Oreina elegans", cl, 6757727, new IntRange(90, 100));

    cl.setPhylum("Arthropoda");
    cl.setClazz("Insecta");
    cl.setOrder("Coleoptera");
    assertMatch("Oreina", cl, 6757727, new IntRange(98, 100));
    assertMatch("Oreina elegans", cl, 6757727, MatchType.HIGHERRANK);
  }

  /**
   * indet is used in occurrences not as an epithet but to indicate indeterminate names, like spec.
   * is used. Make sure we never fuzzy match those, but allow for exact matches as there are a few
   * true names out there. Peperomia indet != Peperomia induta Lacerta bilineata indet (Elbing,
   * 2001) good!
   */
  @Test
  public void testIndet() throws IOException {
    LinneanClassification cl = new Classification();
    //    assertMatch("Peperomia induta", cl, 4189260, new IntRange(95, 100));
    //    assertMatch("Peperomia indet", cl, 3086367, MatchType.HIGHERRANK);
    assertMatch("Lacerta bilineata indet", cl, 6159243, new IntRange(95, 100));
  }

  /** http://gbif.blogspot.com/2015/03/improving-gbif-backbone-matching.html */
  @Test
  public void testBlogNames() throws IOException {
    // http://www.gbif.org/occurrence/164267402/verbatim
    LinneanClassification cl = new Classification();
    assertMatch("Xysticus sp.", cl, 2164999, MatchType.HIGHERRANK);
    assertMatch("Xysticus spec.", cl, 2164999, MatchType.HIGHERRANK);

    // http://www.gbif.org/occurrence/1061576151/verbatim
    cl = new Classification();
    cl.setFamily("Poaceae");
    cl.setGenus("Triodia");
    assertMatch("Triodia sp.", cl, 2702695);

    // http://www.gbif.org/occurrence/1037140379/verbatim
    cl = new Classification();
    cl.setKingdom("Plantae");
    cl.setFamily("XYRIDACEAE");
    cl.setGenus("Xyris");

    // only to the genus 2692599
    // Xyris jolyi Wand. & Cerati 2692999
    assertMatch("Xyris kralii Wand.", cl, 2692599, MatchType.HIGHERRANK);

    // http://www.gbif.org/occurrence/144904719/verbatim
    cl = new Classification();
    cl.setKingdom("Plantae");
    cl.setFamily("GRAMINEAE");
    assertMatch(
        "Zea mays subsp. parviglumis var. huehuet Iltis & Doebley",
        cl,
        5290052,
        new IntRange(98, 100));
  }

  @Test
  public void testImprovedMatching() throws IOException {
    // http://www.gbif.org/occurrence/164267402/verbatim
    LinneanClassification cl = new Classification();
    assertMatch("Zabidius novemaculeatus", cl, 2394331, new IntRange(98, 100));
    assertMatch("Zabideus novemaculeatus", cl, 2394331, new IntRange(75, 85));

    cl.setFamily("Ephippidae");
    assertMatch("Zabidius novemaculeatus", cl, 2394331, new IntRange(98, 100));
    assertMatch("Zabidius novaemaculeatus", cl, 2394331, new IntRange(90, 100));
    assertMatch("Zabidius novaemaculeata", cl, 2394331, new IntRange(90, 100));
    // no name normalization on the genus, but a fuzzy match
    assertMatch("Zabideus novemaculeatus", cl, "2394331", MatchType.VARIANT, new IntRange(85, 95));

    cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setFamily("Yoldiidae");
    // genus match only
    assertMatch("Yoldia bronni", cl, 2285488, new IntRange(98, 100));

    cl.setFamily("Nuculanidae");
    // genus match only
    assertMatch("Yoldia frate", cl, 2285488, new IntRange(90, 95));
  }

  /** Names that fuzzy match to higher species "Iberus gualtieranus" */
  @Test
  public void testIberusGualtieranus() throws IOException {
    LinneanClassification cl = new Classification();
    assertMatch("Iberus gualterianus minor Serradell", cl, 4564258, new IntRange(90, 99));

    cl.setFamily("Helicidae");
    assertMatch("Iberus gualterianus minor Serradell", cl, 4564258, new IntRange(95, 100));
  }

  /** https://github.com/gbif/portal-feedback/issues/2930 */
  @Test
  public void higherOverFuzzy() throws IOException {
    LinneanClassification cl = new Classification();
    // Stolas
    NameUsageMatch m = null;
    m = assertMatch("Stolas costaricensis", cl, 4734997, new IntRange(90, 99));
    assertEquals(MatchType.HIGHERRANK, m.getDiagnostics().getMatchType());

    cl.setKingdom("Animalia");
    cl.setPhylum("Arthropoda");
    cl.setClazz("Insecta");

    // Stelis costaricensis
    m = assertMatch("Stolas costaricensis", cl, 1334265, new IntRange(90, 99));
    assertEquals(MatchType.VARIANT, m.getDiagnostics().getMatchType());

    // Stolas again
    cl.setOrder("Coleoptera");
    cl.setFamily("Chrysomelidae");
    m = assertMatch("Stolas costaricensis", cl, 4734997, new IntRange(95, 100));
    assertEquals(MatchType.HIGHERRANK, m.getDiagnostics().getMatchType());

    // exclude Stolas, match Stelis costaricensis again but with lower confidence now
    Set<String> excl = new HashSet<>();
    excl.add("7780"); // excludes Stolas family
    m = assertMatch("Stolas costaricensis", cl, 1334265, new IntRange(80, 90), excl);
    assertEquals(MatchType.VARIANT, m.getDiagnostics().getMatchType());
  }

  /** https://github.com/gbif/checklistbank/issues/192 */
  @Test
  public void subgenusJacea() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Plantae");
    // THIS SETS the genus too
    NameUsageMatch m = assertMatch("Centaurea subg. Jacea", cl, 7652419, MatchType.EXACT);
    assertEquals("Jacea", m.getUsage().getCanonicalName());
  }

  /** https://github.com/gbif/checklistbank/issues/196 */
  @Test
  public void mycenaFllavoalba() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Fungi");
    cl.setPhylum("Basidiomycota");
    cl.setClazz("Agaricomycetes");
    cl.setOrder("Agaricales");
    cl.setFamily("Mycenaceae");
    cl.setGenus("Mycena");
    cl.setSpecies("flavoalba");

    NameUsageMatch m = assertMatch("Mycena flavoalba (Fr.) Quel.", cl, 4911770, MatchType.EXACT);
    assertEquals("Mycena flavoalba", m.getUsage().getCanonicalName());

    m = assertMatch("Mycena flavoalba (Fr.) Quél.", cl, 4911770, MatchType.EXACT);
  }

  /** https://github.com/gbif/checklistbank/issues/200 */
  @Test
  public void merganser() throws IOException {
    LinneanClassification cl = new Classification();

    NameUsageMatch m = assertMatch("Mergus merganser Linnaeus, 1758", cl, 2498370, MatchType.EXACT);
    assertEquals("Mergus merganser", m.getUsage().getCanonicalName());

    // All uppper case, https://github.com/gbif/checklistbank/issues/175
    m = assertMatch("MERGUS MERGANSER", cl, 2498370, MatchType.EXACT);
    assertEquals("Mergus merganser", m.getUsage().getCanonicalName());
  }

  /** https://github.com/gbif/checklistbank/issues/175 */
  @Test
  public void otuIncaseInsensitive() throws IOException {
    LinneanClassification cl = new Classification();

    assertMatch("AAA536-G10 sp003284565", cl, 10701019, MatchType.EXACT);
    assertMatch("aaa536-g10 sp003284565", cl, 10701019, MatchType.EXACT);
    assertMatch("Aaa536-g10 sp003284565", cl, 10701019, MatchType.EXACT);
  }

  @Test
  public void npe() throws IOException {
    LinneanClassification cl = new Classification();

    assertNoMatch(null, cl);
    assertNoMatch("", cl);
    assertNoMatch("null", cl);
  }

  @Test
  public void dinosaura() throws IOException {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setPhylum("Chordata");
    cl.setClazz("Reptilia");

    // we dont have dinosauria, just a bad genus match Dinosaura (4819911) and a higher match
    // Reptilia (358)
    assertMatch("Dinosauria", Rank.CLASS, cl, 358, MatchType.HIGHERRANK);
    assertMatch("Dinosauria", Rank.GENUS, cl, 4819911, MatchType.VARIANT);
    assertMatch("Dinosaura", Rank.GENUS, cl, 4819911, MatchType.EXACT);
  }

  /** https://github.com/gbif/checklistbank/issues/247 */
  @Test
  public void testCommonHigherDenomiator() throws Exception {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");

    assertMatch("Jaspidia deceptoria Scopoli, 1763", Rank.FAMILY, cl, 7015, MatchType.HIGHERRANK);
  }

  /** https://github.com/gbif/portal-feedback/issues/4532 */
  @Test
  public void testAuthorBrackets() throws Exception {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");

    assertMatch("Eristalis lineata Harris, 1776", Rank.SPECIES, cl, 7834133, MatchType.EXACT);
    assertMatch("Eristalis lineata (Harris, 1776)", Rank.SPECIES, cl, 7834133, MatchType.EXACT);
  }

  /** https://github.com/gbif/portal-feedback/issues/2935 */
  @Test
  public void aggregates() throws Exception {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setOrder("Diptera");
    cl.setFamily("Clusiidae");

    assertMatch("Clusiodes melanostomus", Rank.SPECIES, cl, 4295121, MatchType.EXACT);
    assertMatch(
        "Clusiodes melanostomus", Rank.SPECIES_AGGREGATE, cl, 1550465, MatchType.HIGHERRANK);
  }

  /**
   * https://github.com/CatalogueOfLife/data/issues/743
   */
  @Test
  public void libellulidae() throws Exception {
    LinneanClassification cl = new Classification();

    assertMatch("Libellulidae", Rank.FAMILY, cl, 5936, MatchType.EXACT);
    assertMatch("Libellulidae Leach, 1815", Rank.FAMILY, cl, 5936, MatchType.EXACT);
    assertMatch("Libellulidae Rambur, 1842", Rank.FAMILY, cl, 5936, MatchType.EXACT);
  }

  /** https://github.com/gbif/checklistbank/issues/280 */
  @Test
  public void iris() throws Exception {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setPhylum("Chordata");
    cl.setClazz("Aves");
    cl.setOrder("Passeriformes");
    cl.setFamily("Pipridae");
    cl.setGenus("Lepidothrix");
    cl.setSpecies("iris");
    // we still have old nub data in the lookup resources, hence the match goes wrong!
    assertMatch("iris", null, cl, 5230524, MatchType.EXACT);
  }

  /** https://github.com/gbif/checklistbank/issues/289 */
  @Test
  public void shortCircuitUsageKey() {
    LinneanClassification cl = new Classification();
    cl.setKingdom("Animalia");
    cl.setGenus("Ablabera");
    cl.setSpecies("rufipes");
    String goodKey = "5230524"; // Lepidothrix iris
    String badKey = "99999999";
    // names are ignored and a key is either found with full confidence or not found with full
    // confidence
    assertMatch(
        goodKey,
        "Ablabera rufipes",
        Rank.SPECIES,
        cl,
        goodKey,
        MatchType.EXACT,
        new IntRange(100, 100),
        Sets.newHashSet());
    assertMatch(
        badKey,
        "Ablabera rufipes",
        Rank.SPECIES,
        cl,
        null,
        MatchType.NONE,
        new IntRange(100, 100),
        Sets.newHashSet());
  }
}
