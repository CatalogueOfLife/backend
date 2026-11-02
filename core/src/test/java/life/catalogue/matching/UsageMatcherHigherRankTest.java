package life.catalogue.matching;

import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The higher rank fallback for names the reference lacks, cases from GBIF's matching-ws tests.
 */
public class UsageMatcherHigherRankTest {
  // canonical names index ids
  static final Map<String, Integer> NIDX = Map.of("Xysticus", 10, "Panthera", 20, "Panthera pardus", 21, "Reptilia", 30, "Chordata", 31,
    "Myosotis", 40, "Boraginaceae", 41, "Navicula", 50);
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "an", "Animalia", Rank.KINGDOM, null);
    add(store, "ch", "Chordata", Rank.PHYLUM, "an");
    add(store, "re", "Reptilia", Rank.CLASS, "ch");
    add(store, "ma", "Mammalia", Rank.CLASS, "ch");
    add(store, "pan", "Panthera", Rank.GENUS, "ma");
    add(store, "par", "Panthera pardus", Rank.SPECIES, "pan");
    add(store, "ar", "Arthropoda", Rank.PHYLUM, "an");
    add(store, "xy", "Xysticus", Rank.GENUS, "ar");
    add(store, "pl", "Plantae", Rank.KINGDOM, null);
    add(store, "bo", "Boraginaceae", Rank.FAMILY, "pl");
    add(store, "my", "Myosotis", Rank.GENUS, "bo");
    add(store, "ca", "Caryophyllaceae", Rank.FAMILY, "pl");
    add(store, "ce", "Cerastium", Rank.GENUS, "ca");
    var mill = new SimpleNameCached("my2", "Myosotis", Rank.GENUS);
    mill.setParent("ce");
    mill.setStatus(TaxonomicStatus.SYNONYM);
    mill.setNamesIndexId(NIDX.get("Myosotis"));
    store.add(mill);
    add(store, "na1", "Navicula", Rank.GENUS, "bo");
    add(store, "na2", "Navicula", Rank.GENUS, "bo");

    var ni = mock(NameIndex.class);
    when(ni.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      SimpleName sn = inv.getArgument(0);
      var nidx = NIDX.get(sn.getName());
      return nidx == null ? NameMatch.noMatch() : NameMatch.match(nidx);
    });
    matcher = new UsageMatcher(1, ni, store, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, Rank rank, String parent) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setParent(parent);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    sn.setNamesIndexId(NIDX.get(name));
    store.add(sn);
  }

  UsageMatch match(String name, Rank rank, SimpleNameCached... classification) throws Exception {
    var q = new SimpleNameCached(null, name, rank);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    var snc = new SimpleNameClassified<SimpleNameCached>(q);
    snc.setClassification(Arrays.asList(classification));
    return matcher.match(snc, false, false, true);
  }

  static void assertHigher(String id, UsageMatch m) {
    assertTrue(m.isMatch());
    assertEquals(id, m.usage.getId());
    assertEquals(MatchType.HIGHERRANK, m.type);
  }

  @Test
  public void indetermined() throws Exception {
    assertHigher("xy", match("Xysticus sp.", null));
    assertHigher("xy", match("Xysticus spec.", null));
    assertHigher("par", match("Panthera pardus ssp.", null));
  }

  /**
   * Alternatives are parsed as an informal name anchored on what they share. They are no identification of the first one.
   */
  @Test
  public void informalAlternatives() throws Exception {
    assertHigher("pan", match("Panthera pardus/leo", null));
    assertHigher("pan", match("Panthera pardus or leo", null));
    assertHigher("par", match("Panthera pardus orientalis/japonensis", null));
    // alternatives of two genera share nothing
    assertFalse(match("Panthera pardus / Xysticus cristatus", null).isMatch());
  }

  /**
   * The names index key of an informal name can be the one of its anchor, which does not make it a variant of it.
   */
  @Test
  public void informalAnchorSharesNamesIndexId() throws Exception {
    assertHigher("par", matchIndexed("Panthera pardus orientalis/japonensis", "Panthera pardus", MatchingMode.RELAXED));
    // a higher rank match, which STRICT never does
    assertFalse(matchIndexed("Panthera pardus orientalis/japonensis", "Panthera pardus", MatchingMode.STRICT).isMatch());
    // the name itself is still the name itself
    var m = matchIndexed("Panthera pardus", "Panthera pardus", MatchingMode.STRICT);
    assertEquals("par", m.usage.getId());
    assertEquals(MatchType.EXACT, m.type);
    // a phrase without alternatives can be the taxon itself, this one is a broken year
    m = matchIndexed("Panthera pardus Linnaeus, 175", "Panthera pardus", MatchingMode.RELAXED);
    assertEquals("par", m.usage.getId());
    assertEquals(MatchType.VARIANT, m.type);
  }

  /**
   * The classification tells Myosotis L. from the synonym Myosotis Mill., as it does for the bare genus.
   */
  @Test
  public void genusToldApartByClassification() throws Exception {
    var family = new SimpleNameCached(null, "Boraginaceae", Rank.FAMILY);
    assertHigher("my", match("Myosotis laxa/scorpioides", null, family));
    assertHigher("my", match("Myosotis sp.", null, family));
    assertHigher("my", match("Myosotis xxyyzz", null, family));
    // real homonyms stay unresolved and the walk goes on
    assertHigher("bo", match("Navicula xxyyzz", null, family));
  }

  UsageMatch matchIndexed(String name, String anchor, MatchingMode mode) throws Exception {
    var q = new SimpleNameCached(null, name, Rank.UNRANKED);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    q.setNamesIndexId(NIDX.get(anchor));
    return matcher.match(new SimpleNameClassified<>(q), mode, false);
  }

  /**
   * Dinosauria is no class in the reference, but the query places it in class Reptilia.
   */
  @Test
  public void sameRankClassification() throws Exception {
    assertHigher("re", match("Dinosauria", Rank.CLASS,
      new SimpleNameCached(null, "Reptilia", Rank.CLASS), new SimpleNameCached(null, "Chordata", Rank.PHYLUM)));
    // the name itself at its own rank is no higher taxon
    assertHigher("ch", match("Dinosauria", Rank.CLASS,
      new SimpleNameCached(null, "Dinosauria", Rank.CLASS), new SimpleNameCached(null, "Chordata", Rank.PHYLUM)));
  }

  /**
   * A query without a usable name, like epithets without any genus, can still match its classification.
   */
  @Test
  public void classificationOnly() throws Exception {
    var chordata = new SimpleNameCached(null, "Chordata", Rank.PHYLUM);
    assertHigher("re", matcher.matchClassification(
      List.of(new SimpleNameCached(null, "Reptilia", Rank.CLASS), chordata), MatchingMode.RELAXED, false));
    // unknown lower taxa are skipped
    assertHigher("ch", matcher.matchClassification(
      List.of(new SimpleNameCached(null, "Dinosauria", Rank.ORDER), chordata), MatchingMode.RELAXED, false));
    assertFalse(matcher.matchClassification(List.of(), MatchingMode.RELAXED, false).isMatch());
    assertFalse(matcher.matchClassification(null, MatchingMode.RELAXED, false).isMatch());
    // a classification only gives a higher rank match, which STRICT never does
    var reptiles = List.of(new SimpleNameCached(null, "Reptilia", Rank.CLASS));
    assertFalse(matcher.matchClassification(reptiles, MatchingMode.STRICT, false).isMatch());
  }
}
