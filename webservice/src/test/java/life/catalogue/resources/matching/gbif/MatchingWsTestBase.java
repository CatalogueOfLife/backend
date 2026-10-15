package life.catalogue.resources.matching.gbif;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.parser.NameParser;
import life.catalogue.resources.matching.GbifSpeciesMatchResource;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import static org.junit.Assert.*;

/**
 * Assertions of GBIF's matching-ws tests (https://github.com/gbif/matching-ws, Apache License 2.0) against the
 * GBIF compatible species match, running over the matching-ws test index in {@link MatchingWsIndex}.
 * The cases keep their expected keys and match types; their confidence ranges are dropped.
 */
public abstract class MatchingWsTestBase {
  static GbifSpeciesMatchResource resource;
  /**
   * With -Dmatchingws.soft=true a failing case is recorded and the test carries on with the next case,
   * so a review sees every case, not only the first failure of each test.
   */
  static final boolean SOFT = Boolean.getBoolean("matchingws.soft");
  static final List<String> FAILURES = new ArrayList<>();

  /**
   * Why a case does not pass: a gap ChecklistBank still has, or a deliberate difference to matching-ws.
   */
  enum Known {
    FUZZY(false, "no fuzzy matching of misspelt names yet"),
    EXCLUDE(false, "excluding taxa by key is not supported yet"),
    HOMONYMS(false, "homonyms without evidence should give their lowest shared higher taxon or nothing, CatalogueOfLife/backend#1651"),
    AUTHOR_TIE(false, "of two compatible authorships the closer one should win, CatalogueOfLife/backend#1652"),
    OTU_CASE(false, "a lowercase BOLD BIN is not normalised by the name parser, gbif/name-parser-rust#119"),
    KINGDOM(true, "a name contradicting the supplied kingdom is no match, not the kingdom, CatalogueOfLife/backend#1650"),
    GENUS_GROUP(true, "the genus group ranks stay strict, a subgenus is no genus, CatalogueOfLife/backend#1661"),
    RANK_RETRY(true, "a binomial sent as an aggregate is matched again with its parsed rank and finds the species"),
    INDET(true, "indet marks an indetermined name and is no epithet"),
    YEAR_ONLY(true, "a year-only authorship needs a candidate with a year");

    final boolean deliberate;
    final String reason;

    Known(boolean deliberate, String reason) {
      this.deliberate = deliberate;
      this.reason = reason;
    }
  }

  private static Known pending;

  /**
   * Marks the next case as known to fail. A marked case that passes fails the test, so the marker gets removed.
   */
  static void known(Known why) {
    pending = why;
  }

  @BeforeClass
  public static void buildIndex() throws Exception {
    if (resource == null) {
      resource = MatchingWsIndex.resource();
    }
  }

  @AfterClass
  public static void report() {
    if (SOFT) {
      System.out.println("\nMATCHINGWS " + FAILURES.size() + " failed cases");
      FAILURES.forEach(f -> System.out.println("MATCHINGWS " + f));
      FAILURES.clear();
    }
  }

  private static String caller() {
    // the outermost frame of a test class is the test method
    String found = "?";
    for (var e : Thread.currentThread().getStackTrace()) {
      if (e.getClassName().endsWith("Test") && !e.getClassName().endsWith("TestBase")) {
        found = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
      }
    }
    return found;
  }

  /**
   * Runs a case, in soft mode recording its failure instead of throwing it.
   */
  static void check(String name, Runnable assertion, GbifNameUsageMatch best) {
    final Known why = pending;
    pending = null;
    try {
      assertion.run();
    } catch (AssertionError e) {
      if (why != null) {
        System.out.println("  known " + (why.deliberate ? "difference" : "gap") + " " + why + ": " + why.reason);
        return;
      }
      if (!SOFT) throw e;
      String got = best == null ? "?" : (best.usage == null ? "-" : best.usage.key + " " + best.usage.name + " [" + best.usage.rank + " " + best.usage.status + "]")
        + " " + best.diagnostics.matchType + " conf=" + best.diagnostics.confidence;
      FAILURES.add(caller() + " | " + name + " | " + e.getMessage() + " | got " + got);
      return;
    }
    if (why != null) {
      String msg = name + " is marked as known " + why + " but passes now, remove the marker";
      if (!SOFT) fail(msg);
      FAILURES.add(caller() + " | " + msg);
    }
  }

  static Classification cl() {
    return new Classification();
  }

  static GbifNameUsageMatch query(@Nullable String usageKey, String name, @Nullable Rank rank, Classification cl, boolean strict) {
    return resource.match(null, usageKey, null, name, null, null, rank == null ? null : rank.name(), null, null, null,
      null, null, null, strict, true, cl);
  }

  static GbifNameUsageMatch match(String name, Classification cl) {
    return query(null, name, null, cl, false);
  }

  static GbifNameUsageMatch assertMatch(String name, Classification cl, Object expectedKey) {
    return assertMatch(null, name, null, cl, expectedKey, null, false);
  }

  static GbifNameUsageMatch assertMatch(String name, Classification cl, Object expectedKey, MatchType type) {
    return assertMatch(null, name, null, cl, expectedKey, type, false);
  }

  static GbifNameUsageMatch assertMatch(String name, Rank rank, Classification cl, Object expectedKey, MatchType type) {
    return assertMatch(null, name, rank, cl, expectedKey, type, false);
  }

  /**
   * matching-ws excludes taxa by key, which the CLB matcher does not support yet. The exclusion is ignored here.
   */
  static GbifNameUsageMatch assertMatch(String name, Classification cl, Object expectedKey, MatchType type, Set<String> exclude) {
    return assertMatch(null, name, null, cl, expectedKey, type, false);
  }

  /**
   * @param expectedKey the expected usage key, null for no match
   * @param type the expected match type, null for any but NONE
   */
  static GbifNameUsageMatch assertMatch(@Nullable String usageKey, String name, @Nullable Rank rank, Classification cl,
                                        @Nullable Object expectedKey, @Nullable MatchType type, boolean strict) {
    var best = query(usageKey, name, rank, cl, strict);
    print(name, best);
    check(name, () -> {
      if (expectedKey == null) {
        assertNull("Wrong expected key", best.usage);
      } else {
        assertEquals("Wrong expected key", String.valueOf(expectedKey), best.usage == null ? null : best.usage.key);
      }
      if (type == null) {
        assertNotEquals("Wrong none match type", MatchType.NONE.name(), best.diagnostics.matchType);
      } else {
        assertEquals("Wrong match type", type.name(), best.diagnostics.matchType);
      }
      assertMatchConsistency(best);
    }, best);
    return best;
  }

  /**
   * Asserts the accepted name of the match, for synonyms of one accepted name the query cannot choose between.
   */
  static void assertAccepted(String name, Classification cl, Object acceptedKey) {
    var best = match(name, cl);
    print(name, best);
    check(name, () -> {
      assertNotNull("No match", best.usage);
      String accepted = best.synonym && best.acceptedUsage != null ? best.acceptedUsage.key : best.usage.key;
      assertEquals("Wrong accepted key", String.valueOf(acceptedKey), accepted);
      assertMatchConsistency(best);
    }, best);
  }

  static void assertNoMatch(String name, Classification cl) {
    var best = match(name, cl);
    print(name, best);
    check(name, () -> {
      assertEquals(MatchType.NONE.name(), best.diagnostics.matchType);
      assertNull(best.usage);
      assertMatchConsistency(best);
    }, best);
  }

  static String nameFor(GbifNameUsageMatch m, Rank rank) {
    var rn = rankedName(m, rank);
    return rn == null ? null : rn.name;
  }

  static String keyFor(GbifNameUsageMatch m, Rank rank) {
    var rn = rankedName(m, rank);
    return rn == null ? null : rn.key;
  }

  private static GbifNameUsageMatch.RankedName rankedName(GbifNameUsageMatch m, Rank rank) {
    if (m.classification != null) {
      for (var rn : m.classification) {
        if (rank.name().equals(rn.rank)) {
          return rn;
        }
      }
    }
    return null;
  }

  static void assertMatchConsistency(GbifNameUsageMatch match) {
    assertNotNull(match.diagnostics.matchType);
    if (MatchType.NONE.name().equals(match.diagnostics.matchType)) {
      // there is no match to be confident about
      assertNull(match.diagnostics.confidence);
      assertNull(match.usage);
      for (var r : new Rank[]{Rank.SPECIES, Rank.GENUS, Rank.FAMILY, Rank.ORDER, Rank.CLASS, Rank.PHYLUM, Rank.KINGDOM}) {
        assertNull(keyFor(match, r));
        assertNull(nameFor(match, r));
      }

    } else {
      assertNotNull(match.diagnostics.confidence);
      assertNotNull(match.usage.key);
      assertNotNull(match.usage.name);

      if (match.usage.rank != null && isParsed(match)) {
        Rank rank = Rank.valueOf(match.usage.rank);
        if (rank.isSuprageneric()) {
          assertNull(nameFor(match, Rank.SPECIES));
          assertNull(keyFor(match, Rank.SPECIES));
          assertNull(nameFor(match, Rank.GENUS));
          assertNull(keyFor(match, Rank.GENUS));

        } else if (rank == Rank.GENUS) {
          assertNotNull(nameFor(match, Rank.GENUS));
          assertNull(nameFor(match, Rank.SPECIES));
          assertNull(keyFor(match, Rank.SPECIES));

        } else if (rank == Rank.SPECIES) {
          assertNotNull(nameFor(match, Rank.SPECIES));
          assertNotNull(keyFor(match, Rank.SPECIES));
          if (!match.synonym) {
            assertEquals(match.usage.key, keyFor(match, Rank.SPECIES));
            assertTrue(match.usage.name.startsWith(nameFor(match, Rank.SPECIES)));
          }

        } else if (rank.isInfraspecific()) {
          assertNotNull(nameFor(match, Rank.SPECIES));
          if (!match.synonym) {
            assertNotEquals(match.usage.key, keyFor(match, Rank.SPECIES));
            assertTrue(match.usage.name.startsWith(nameFor(match, Rank.SPECIES)));
          }
        }
      }
    }
  }

  private static boolean isParsed(GbifNameUsageMatch m) {
    if (m.usage.name != null) {
      try {
        return NameParser.PARSER.parse(SimpleName.sn(m.usage.name)).isPresent();
      } catch (Exception e) {
        // unparsable
      }
    }
    return false;
  }

  static void print(String name, GbifNameUsageMatch best) {
    System.out.println("\n" + name + " matches " + (best.usage != null ? best.usage.name + " [" + best.usage.key + "]" : "nothing")
      + " " + best.diagnostics.matchType + " with confidence " + best.diagnostics.confidence);
    if (best.usage != null) {
      System.out.println("  " + String.join("; ", String.valueOf(nameFor(best, Rank.KINGDOM)), String.valueOf(nameFor(best, Rank.PHYLUM)),
        String.valueOf(nameFor(best, Rank.CLASS)), String.valueOf(nameFor(best, Rank.ORDER)), String.valueOf(nameFor(best, Rank.FAMILY))));
      if (best.diagnostics.note != null) {
        System.out.println("  " + best.diagnostics.note);
      }
    }
    if (best.diagnostics.alternatives != null) {
      for (var m : best.diagnostics.alternatives) {
        System.out.println("  Alt: " + m.usage.name + " [" + m.usage.key + "] " + nameFor(m, Rank.KINGDOM) + ","
          + nameFor(m, Rank.PHYLUM) + "," + nameFor(m, Rank.CLASS) + "," + nameFor(m, Rank.ORDER) + "," + nameFor(m, Rank.FAMILY)
          + "," + nameFor(m, Rank.GENUS) + "," + nameFor(m, Rank.SPECIES) + ". " + m.diagnostics.matchType);
      }
    }
  }
}
