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
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A placeholder has no names index id, but matches the taxon it is anchored on as a higher rank match.
 */
public class UsageMatcherAnchorTest {
  // canonical names index ids. Placeholders have none
  static final Map<String, Integer> NIDX = Map.of("Animalia", 1, "Mollusca", 10, "Cisticola", 20, "Oenanthe", 30,
    "Plantae", 2, "Ecdysozoa", 40);
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "an", "Animalia", Rank.KINGDOM, null);
    add(store, "mo", "Mollusca", Rank.PHYLUM, "an");
    add(store, "ci", "Cisticola", Rank.GENUS, "an");
    add(store, "oe", "Oenanthe", Rank.GENUS, "an");
    add(store, "ec", "Ecdysozoa", Rank.UNRANKED, "an");
    add(store, "pl", "Plantae", Rank.KINGDOM, null);
    add(store, "oe2", "Oenanthe", Rank.GENUS, "pl");

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

  static SimpleNameClassified<SimpleNameCached> placeholder(String name, Rank rank, SimpleNameCached... classification) {
    var q = new SimpleNameCached(null, name, rank);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    var snc = new SimpleNameClassified<SimpleNameCached>(q);
    snc.setClassification(Arrays.asList(classification));
    return snc;
  }

  UsageMatch merge(String name, Rank rank, SimpleNameCached... classification) throws Exception {
    return matcher.match(placeholder(name, rank, classification), true, false);
  }

  static void assertAnchor(String id, UsageMatch m) {
    assertTrue(m.isMatch());
    assertEquals(id, m.usage.getId());
    assertEquals(MatchType.HIGHERRANK, m.type);
  }

  @Test
  public void anchor() throws Exception {
    var animalia = new SimpleNameCached(null, "Animalia", Rank.KINGDOM);
    assertAnchor("mo", merge("Mollusca incertae sedis", Rank.CLASS, animalia));
    // no classification at all, a root placeholder
    assertAnchor("mo", merge("Mollusca incertae sedis", Rank.ORDER));
    // an unranked placeholder can hang off any ranked taxon
    assertAnchor("mo", merge("Mollusca incertae sedis", null));
    assertAnchor("mo", merge("Mollusca incertae sedis", Rank.UNRANKED));
    // a species placeholder anchored on its genus
    assertAnchor("ci", merge("Cisticola unnamed", Rank.SPECIES, animalia));
  }

  /**
   * Only a usage ranked above the placeholder is the taxon it hangs off
   */
  @Test
  public void anchorNotAbove() throws Exception {
    var m = merge("Mollusca incertae sedis", Rank.PHYLUM);
    assertFalse(m.isMatch());
    // a merge never inserts or merges a name without a names index id
    assertEquals(MatchType.UNSUPPORTED, m.type);
    assertTrue(m.ignore);
    assertFalse(merge("Mollusca incertae sedis", Rank.KINGDOM).isMatch());
    // an unranked usage is above nothing
    assertFalse(merge("Ecdysozoa incertae sedis", Rank.FAMILY).isMatch());
  }

  @Test
  public void noAnchor() throws Exception {
    // not in the reference
    assertFalse(merge("Nemertea incertae sedis", Rank.FAMILY).isMatch());
    // no anchor at all
    assertFalse(merge("Incertae sedis", Rank.FAMILY).isMatch());
    assertFalse(merge("[unassigned] Cladobranchia", Rank.ORDER).isMatch());
  }

  @Test
  public void homonymAnchor() throws Exception {
    assertAnchor("oe", merge("Oenanthe incertae sedis", Rank.SPECIES, new SimpleNameCached(null, "Animalia", Rank.KINGDOM)));
    assertAnchor("oe2", merge("Oenanthe incertae sedis", Rank.SPECIES, new SimpleNameCached(null, "Plantae", Rank.KINGDOM)));
  }

  /**
   * An external query gets the anchor in RELAXED mode, never in STRICT mode which makes no higher rank matches
   */
  @Test
  public void modes() throws Exception {
    assertAnchor("mo", matcher.match(placeholder("Mollusca incertae sedis", Rank.CLASS), MatchingMode.RELAXED, false));
    assertFalse(matcher.match(placeholder("Mollusca incertae sedis", Rank.CLASS), MatchingMode.STRICT, false).isMatch());
  }
}
