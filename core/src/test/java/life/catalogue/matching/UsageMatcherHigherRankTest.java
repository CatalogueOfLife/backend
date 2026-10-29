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
  static final Map<String, Integer> NIDX = Map.of("Xysticus", 10, "Panthera", 20, "Panthera pardus", 21, "Reptilia", 30, "Chordata", 31);
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
