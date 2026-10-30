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
 * A name cited with only the author of its basionym is matched by external queries, merges stay strict.
 * See https://github.com/CatalogueOfLife/backend/issues/1660
 */
public class UsageMatcherBasionymAuthorTest {
  static final Map<String, Integer> NIDX = Map.of("Stellaria", 1, "Stellaria media", 2, "Abies", 3, "Abies alba", 4);
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "st", "Stellaria", "L.", Rank.GENUS, null, TaxonomicStatus.ACCEPTED);
    add(store, "acc", "Stellaria media", "(L.) Vill.", Rank.SPECIES, "st", TaxonomicStatus.ACCEPTED);
    add(store, "syn1", "Stellaria media", "Sibth.", Rank.SPECIES, "st", TaxonomicStatus.SYNONYM);
    add(store, "syn2", "Stellaria media", "Wight ex Edgew.", Rank.SPECIES, "st", TaxonomicStatus.SYNONYM);
    add(store, "ab", "Abies", "Mill.", Rank.GENUS, null, TaxonomicStatus.ACCEPTED);
    add(store, "aa", "Abies alba", "Mill.", Rank.SPECIES, "ab", TaxonomicStatus.ACCEPTED);
    add(store, "aa2", "Abies alba", "(Mill.) Smith", Rank.SPECIES, "ab", TaxonomicStatus.SYNONYM);

    var ni = mock(NameIndex.class);
    when(ni.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      SimpleName sn = inv.getArgument(0);
      var nidx = NIDX.get(sn.getName());
      return nidx == null ? NameMatch.noMatch() : NameMatch.match(nidx);
    });
    matcher = new UsageMatcher(1, ni, store, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, String authorship, Rank rank, String parent, TaxonomicStatus status) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setAuthorship(authorship);
    sn.setParent(parent);
    sn.setStatus(status);
    sn.setNamesIndexId(NIDX.get(name));
    store.add(sn);
  }

  static QueryName query(String name, String authorship, Rank rank) {
    var q = new SimpleNameCached(null, name, rank);
    q.setAuthorship(authorship);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    q.setNamesIndexId(NIDX.get(name));
    return new QueryName(q, List.of(), false);
  }

  void assertVariant(String id, UsageMatch m) {
    assertTrue(m.isMatch());
    assertEquals(id, m.usage.getId());
    assertEquals(MatchType.VARIANT, m.type);
  }

  @Test
  public void basionymAuthorCited() throws Exception {
    assertVariant("acc", matcher.match(query("Stellaria media", "L.", Rank.SPECIES), MatchingMode.RELAXED, false));
    assertVariant("acc", matcher.match(query("Stellaria media", "L.", Rank.UNRANKED), MatchingMode.RELAXED, false));
    assertVariant("acc", matcher.match(query("Stellaria media", "L.", Rank.SPECIES), null, false));
    assertVariant("acc", matcher.match(query("Stellaria media", "Linnaeus, 1753", Rank.SPECIES), MatchingMode.RELAXED, false));
  }

  @Test
  public void strictAndMerges() throws Exception {
    assertFalse(matcher.match(query("Stellaria media", "L.", Rank.SPECIES), MatchingMode.STRICT, false).isMatch());
    assertFalse(matcher.match(new SimpleNameClassified<>(query("Stellaria media", "L.", Rank.SPECIES)), false, false).isMatch());
  }

  @Test
  public void otherAuthorsStayDifferent() throws Exception {
    // no basionym author of any candidate: falls back to the genus
    var m = matcher.match(query("Stellaria media", "Smith", Rank.SPECIES), MatchingMode.RELAXED, false);
    assertEquals(MatchType.HIGHERRANK, m.type);
    assertEquals("st", m.usage.getId());
    // a bracketed basionym author of the query is no citation, it already has one
    m = matcher.match(query("Stellaria media", "(Smith) Vill.", Rank.SPECIES), MatchingMode.RELAXED, false);
    assertEquals("acc", m.usage.getId());
    m = matcher.match(query("Stellaria media", "(Smith) Jones", Rank.SPECIES), MatchingMode.RELAXED, false);
    assertEquals(MatchType.HIGHERRANK, m.type);
  }

  @Test
  public void realCombinationAuthorWins() throws Exception {
    var m = matcher.match(query("Abies alba", "Mill.", Rank.SPECIES), MatchingMode.RELAXED, false);
    assertEquals("aa", m.usage.getId());
    assertEquals(MatchType.EXACT, m.type);
  }
}
