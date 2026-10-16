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
 * A rank given apart from an infraspecific name without a rank marker is only a hint for external queries in relaxed
 * mode. Merges stay strict. A trinomial without marker or rank is no subspecies, the rank stays unknown.
 * See https://github.com/CatalogueOfLife/backend/issues/1659
 */
public class UsageMatcherInfraspecificRankTest {
  // canonical names index ids
  static final Map<String, Integer> NIDX = Map.of(
    "Anas platyrhynchos", 10, "Anas platyrhynchos domesticus", 11,
    "Urtica dioica", 20, "Urtica dioica dioica", 21,
    "Abies alba", 30, "Abies alba acutifolia", 31
  );
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "an", "Animalia", Rank.KINGDOM, null, null);
    add(store, "anas", "Anas", Rank.GENUS, null, "an");
    add(store, "ap", "Anas platyrhynchos", Rank.SPECIES, "Anas platyrhynchos", "anas");
    // as in release 315557: the form sits right below the genus, and the stemmed names index puts the subspecies
    // domestica under the same canonical id
    add(store, "apf", "Anas platyrhynchos f. domesticus", Rank.FORM, "Anas platyrhynchos domesticus", "anas");
    add(store, "aps", "Anas platyrhynchos domestica", Rank.SUBSPECIES, "Anas platyrhynchos domesticus", "ap");

    add(store, "pl", "Plantae", Rank.KINGDOM, null, null);
    add(store, "urt", "Urtica", Rank.GENUS, null, "pl");
    add(store, "ud", "Urtica dioica", Rank.SPECIES, "Urtica dioica", "urt");
    add(store, "uds", "Urtica dioica subsp. dioica", Rank.SUBSPECIES, "Urtica dioica dioica", "ud");
    // the autonym variety sits below the autonym subspecies, so its classification is the deeper one
    add(store, "udv", "Urtica dioica var. dioica", Rank.VARIETY, "Urtica dioica dioica", "uds");

    add(store, "abies", "Abies", Rank.GENUS, null, "pl");
    add(store, "aa", "Abies alba", Rank.SPECIES, "Abies alba", "abies");
    add(store, "aav", "Abies alba var. acutifolia", Rank.VARIETY, "Abies alba acutifolia", "aa");
    add(store, "aaf", "Abies alba f. acutifolia", Rank.FORM, "Abies alba acutifolia", "aa");

    var ni = mock(NameIndex.class);
    when(ni.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      SimpleName sn = inv.getArgument(0);
      var nidx = NIDX.get(sn.getName());
      return nidx == null ? NameMatch.noMatch() : NameMatch.match(nidx);
    });
    matcher = new UsageMatcher(1, ni, store, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, Rank rank, String canonical, String parent) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setParent(parent);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    sn.setNamesIndexId(canonical == null ? null : NIDX.get(canonical));
    store.add(sn);
  }

  /**
   * @param canonical the canonical name the query is looked up by in the names index
   */
  static QueryName query(String name, String canonical, Rank rank, boolean unmarkedRank) {
    var q = new SimpleNameCached(null, name, rank);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    q.setNamesIndexId(NIDX.get(canonical));
    return new QueryName(q, List.of(), unmarkedRank);
  }

  /**
   * Matches the query as a merge does: strictly and with nothing but the name itself.
   */
  UsageMatch matchInternal(QueryName q) throws Exception {
    return matcher.match(new SimpleNameClassified<>(q), false, false, true);
  }

  static void assertMatch(String id, UsageMatch m) {
    assertTrue(m.isMatch());
    assertEquals(id, m.usage.getId());
  }

  @Test
  public void unmarkedRankIsHint() throws Exception {
    var q = query("Anas platyrhynchos var. domesticus", "Anas platyrhynchos domesticus", Rank.VARIETY, true);
    assertMatch("apf", matcher.match(q, MatchingMode.RELAXED, false));
    // strict mode keeps the rank, and there is no higher rank fallback either
    assertFalse(matcher.match(q, MatchingMode.STRICT, false).isMatch());
    // merges keep it too
    assertFalse(matcher.match(new SimpleNameClassified<>(q), false, false).isMatch());
    var m = matchInternal(q);
    assertEquals(MatchType.HIGHERRANK, m.type);
    assertMatch("ap", m);
  }

  @Test
  public void spellingIgnoresMarkers() throws Exception {
    // the epithet spelled like the query wins over the stemmed subspecies, although the marker differs
    assertMatch("apf", matcher.match(query("Anas platyrhynchos domesticus", "Anas platyrhynchos domesticus", Rank.UNRANKED, false),
      MatchingMode.RELAXED, false));
    assertMatch("aps", matcher.match(query("Anas platyrhynchos domestica", "Anas platyrhynchos domesticus", Rank.UNRANKED, false),
      MatchingMode.RELAXED, false));
  }

  @Test
  public void withoutRankMarkers() {
    assertEquals("Anas platyrhynchos domesticus", UsageMatcher.withoutRankMarkers("Anas platyrhynchos f. domesticus"));
    assertEquals("Urtica dioica dioica", UsageMatcher.withoutRankMarkers("Urtica dioica subsp. dioica"));
    assertEquals("Aus bus cus dus", UsageMatcher.withoutRankMarkers("Aus bus var. cus nothof. dus"));
    assertEquals("Abies alba", UsageMatcher.withoutRankMarkers("Abies alba"));
    assertNull(UsageMatcher.withoutRankMarkers(null));
  }

  @Test
  public void markedRankStaysStrict() throws Exception {
    // the variety written with its marker is no form
    var q = query("Anas platyrhynchos var. domesticus", "Anas platyrhynchos domesticus", Rank.VARIETY, false);
    var m = matcher.match(q, MatchingMode.RELAXED, false);
    assertEquals(MatchType.HIGHERRANK, m.type);
    assertMatch("ap", m);
  }

  @Test
  public void exactRankWins() throws Exception {
    assertMatch("aav", matcher.match(query("Abies alba var. acutifolia", "Abies alba acutifolia", Rank.VARIETY, true), MatchingMode.RELAXED, false));
    assertMatch("aaf", matcher.match(query("Abies alba f. acutifolia", "Abies alba acutifolia", Rank.FORM, true), MatchingMode.RELAXED, false));
    // a rank neither of them has leaves both
    var m = matcher.match(query("Abies alba subsp. acutifolia", "Abies alba acutifolia", Rank.SUBSPECIES, true), MatchingMode.RELAXED, false);
    assertEquals(MatchType.AMBIGUOUS, m.type);
  }

  @Test
  public void unrankedTrinomial() throws Exception {
    // without marker or rank neither the subspecies nor the variety is the name, whatever GBIF picks
    var q = query("Urtica dioica dioica", "Urtica dioica dioica", Rank.UNRANKED, false);
    assertEquals(MatchType.AMBIGUOUS, matcher.match(q, MatchingMode.RELAXED, false).type);
    assertEquals(MatchType.AMBIGUOUS, matcher.match(q, MatchingMode.STRICT, false).type);
    assertEquals(MatchType.AMBIGUOUS, matchInternal(q).type);

    // the variety written with its marker stays the variety
    var v = query("Urtica dioica var. dioica", "Urtica dioica dioica", Rank.VARIETY, false);
    assertMatch("udv", matcher.match(v, MatchingMode.RELAXED, false));
    // as does an unmarked variety
    v = query("Urtica dioica var. dioica", "Urtica dioica dioica", Rank.VARIETY, true);
    assertMatch("udv", matcher.match(v, MatchingMode.RELAXED, false));
  }
}
