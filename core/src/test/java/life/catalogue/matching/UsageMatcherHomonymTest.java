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
 * Candidates nothing tells apart are only picked from when they are duplicates of one name. Real homonyms, or names
 * that might be, are unresolved: they span disparate groups, have different authorships or lack one to compare.
 * The single accepted name still wins over its synonyms. See https://github.com/CatalogueOfLife/backend/issues/1651
 */
public class UsageMatcherHomonymTest {
  // canonical names index ids
  static final Map<String, Integer> NIDX = Map.ofEntries(
    Map.entry("Navicula", 10), Map.entry("Agathis montana", 20), Map.entry("Aus", 29), Map.entry("Aus bus", 30),
    Map.entry("Aus cus", 31), Map.entry("Aus dus", 32), Map.entry("Aus eus", 33), Map.entry("Aus fus", 34),
    Map.entry("Aus gus", 35), Map.entry("Aus hus", 36), Map.entry("Aus (Aus) hus", 36), Map.entry("Aus ius", 37),
    Map.entry("Aus (Aus)", 29)
  );
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "an", "Animalia", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add(store, "mol", "Mollusca", Rank.PHYLUM, "an", null, TaxonomicStatus.ACCEPTED);
    add(store, "nav", "Navicula", Rank.GENUS, "mol", "Spix, 1827", TaxonomicStatus.ACCEPTED);
    add(store, "ins", "Insecta", Rank.CLASS, "an", null, TaxonomicStatus.ACCEPTED);
    add(store, "aga", "Agathis", Rank.GENUS, "ins", null, TaxonomicStatus.ACCEPTED);
    add(store, "agam", "Agathis montana", Rank.SPECIES, "aga", null, TaxonomicStatus.ACCEPTED);

    add(store, "chr", "Chromista", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add(store, "nav2", "Navicula", Rank.GENUS, "chr", "Bory", TaxonomicStatus.ACCEPTED);

    add(store, "pl", "Plantae", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add(store, "aga2", "Agathis", Rank.GENUS, "pl", null, TaxonomicStatus.ACCEPTED);
    add(store, "agam2", "Agathis montana", Rank.SPECIES, "aga2", null, TaxonomicStatus.ACCEPTED);
    add(store, "aus", "Aus", Rank.GENUS, "pl", "L.", TaxonomicStatus.ACCEPTED);
    add(store, "ausa", "Aus (Aus)", Rank.SUBGENUS, "aus", "L.", TaxonomicStatus.ACCEPTED);
    // real homonyms
    add(store, "bus1", "Aus bus", Rank.SPECIES, "aus", "L.", TaxonomicStatus.ACCEPTED);
    add(store, "bus2", "Aus bus", Rank.SPECIES, "aus", "Mill.", TaxonomicStatus.ACCEPTED);
    // homonyms or duplicates, the missing authorship cannot tell
    add(store, "cus1", "Aus cus", Rank.SPECIES, "aus", "L.", TaxonomicStatus.ACCEPTED);
    add(store, "cus2", "Aus cus", Rank.SPECIES, "aus", null, TaxonomicStatus.ACCEPTED);
    // duplicates of one name
    add(store, "dus1", "Aus dus", Rank.SPECIES, "aus", "L.", TaxonomicStatus.ACCEPTED);
    add(store, "dus2", "Aus dus", Rank.SPECIES, "aus", "Linnaeus", TaxonomicStatus.ACCEPTED);
    // homonyms, but only one of them is accepted
    add(store, "eus1", "Aus eus", Rank.SPECIES, "aus", "L.", TaxonomicStatus.ACCEPTED);
    add(store, "eus2", "Aus eus", Rank.SPECIES, "dus1", "Mill.", TaxonomicStatus.SYNONYM);
    add(store, "eus3", "Aus eus", Rank.SPECIES, "bus1", null, TaxonomicStatus.SYNONYM);
    // a species and an authorless aggregate, synonyms of different taxa
    add(store, "fus1", "Aus fus", Rank.SPECIES, "bus1", "L.", TaxonomicStatus.SYNONYM);
    add(store, "fus2", "Aus fus", Rank.SPECIES_AGGREGATE, "cus1", null, TaxonomicStatus.SYNONYM);
    // the name and two misapplications of it
    add(store, "gus1", "Aus gus", Rank.SPECIES, "bus1", "(L.) Mill.", TaxonomicStatus.SYNONYM);
    add(store, "gus2", "Aus gus", Rank.SPECIES, "cus1", null, TaxonomicStatus.MISAPPLIED);
    add(store, "gus3", "Aus gus", Rank.SPECIES, "dus1", null, TaxonomicStatus.MISAPPLIED);
    // the accepted name shows its subgenus, its homonym synonyms do not
    add(store, "hus1", "Aus (Aus) hus", Rank.SPECIES, "aus", "Boyer, 1841", TaxonomicStatus.ACCEPTED);
    add(store, "hus2", "Aus hus", Rank.SPECIES, "bus1", "Kaltenbach, 1843", TaxonomicStatus.SYNONYM);
    add(store, "hus3", "Aus hus", Rank.SPECIES, "hus1", "Boyer, 1841", TaxonomicStatus.SYNONYM);
    // homonym synonyms of one accepted name and a provisional copy
    add(store, "ius1", "Aus ius", Rank.SPECIES, "bus1", "Boeck, 1865", TaxonomicStatus.SYNONYM);
    add(store, "ius2", "Aus ius", Rank.SPECIES, "bus1", "Lubbock, 1865", TaxonomicStatus.SYNONYM);
    add(store, "ius3", "Aus ius", Rank.SPECIES, "aus", "Boeck, 1865", TaxonomicStatus.PROVISIONALLY_ACCEPTED);

    var ni = mock(NameIndex.class);
    when(ni.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      SimpleName sn = inv.getArgument(0);
      var nidx = NIDX.get(sn.getName());
      return nidx == null ? NameMatch.noMatch() : NameMatch.match(nidx);
    });
    matcher = new UsageMatcher(1, ni, store, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, Rank rank, String parent, String authorship, TaxonomicStatus status) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setParent(parent);
    sn.setAuthorship(authorship);
    sn.setStatus(status);
    sn.setNamesIndexId(NIDX.get(name));
    store.add(sn);
  }

  static SimpleNameClassified<SimpleNameCached> query(String name, Rank rank, SimpleNameCached... classification) {
    return query(name, null, rank, classification);
  }

  static SimpleNameClassified<SimpleNameCached> query(String name, String authorship, Rank rank, SimpleNameCached... classification) {
    var q = new SimpleNameCached(null, name, rank);
    q.setAuthorship(authorship);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    q.setNamesIndexId(NIDX.get(name));
    var snc = new SimpleNameClassified<SimpleNameCached>(q);
    snc.setClassification(Arrays.asList(classification));
    return snc;
  }

  /**
   * Matches as a merge does.
   */
  UsageMatch merge(SimpleNameClassified<SimpleNameCached> q) throws Exception {
    return matcher.match(q, false, true);
  }

  static void assertUnresolved(UsageMatch m) {
    assertFalse(m.isMatch());
    assertTrue(m.unresolvedHomonym);
    assertEquals(MatchType.AMBIGUOUS, m.type);
    assertTrue(m.alternatives.size() > 1);
  }

  static void assertMatch(String id, UsageMatch m) {
    assertTrue(m.isMatch());
    assertEquals(id, m.usage.getId());
  }

  @Test
  public void disparateGroups() throws Exception {
    // a query without any rank used to get the deepest classified genus
    assertUnresolved(merge(query("Navicula", Rank.UNRANKED)));
    assertUnresolved(merge(query("Navicula", Rank.GENUS)));
    var m = matcher.match(query("Navicula", Rank.UNRANKED), MatchingMode.RELAXED, true);
    assertUnresolved(m);
    // a species too
    assertUnresolved(merge(query("Agathis montana", Rank.SPECIES)));
    // the classification still decides
    assertMatch("nav2", merge(query("Navicula", Rank.UNRANKED, new SimpleNameCached(null, "Chromista", Rank.KINGDOM))));
    assertMatch("agam2", merge(query("Agathis montana", Rank.SPECIES, new SimpleNameCached(null, "Plantae", Rank.KINGDOM))));
  }

  @Test
  public void differentAuthorship() throws Exception {
    assertUnresolved(merge(query("Aus bus", Rank.SPECIES)));
    assertUnresolved(merge(query("Aus bus", Rank.UNRANKED)));
  }

  @Test
  public void missingAuthorship() throws Exception {
    assertUnresolved(merge(query("Aus cus", Rank.SPECIES)));
    assertUnresolved(matcher.match(query("Aus cus", Rank.UNRANKED), MatchingMode.STRICT, true));
    // relaxed falls back to the genus instead
    var m = matcher.match(query("Aus cus", Rank.UNRANKED), MatchingMode.RELAXED, true);
    assertEquals(MatchType.HIGHERRANK, m.type);
    assertMatch("aus", m);
  }

  @Test
  public void duplicates() throws Exception {
    var m = merge(query("Aus dus", Rank.SPECIES));
    assertEquals(MatchType.AMBIGUOUS, m.type);
    assertMatch("dus1", m);
  }

  @Test
  public void singleAccepted() throws Exception {
    // the accepted name wins over its homonym synonyms
    assertMatch("eus1", merge(query("Aus eus", Rank.SPECIES)));
  }

  @Test
  public void queryAuthorshipWithoutRank() throws Exception {
    assertMatch("fus1", merge(query("Aus fus", "L.", Rank.UNRANKED)));
    assertMatch("fus1", matcher.match(query("Aus fus", "Linnaeus", Rank.UNRANKED), MatchingMode.STRICT, false));
    // without authorship there is nothing to tell them apart
    assertUnresolved(merge(query("Aus fus", Rank.UNRANKED)));
  }

  @Test
  public void misappliedLoses() throws Exception {
    assertMatch("gus1", merge(query("Aus gus", Rank.SPECIES)));
    assertMatch("gus1", matcher.match(query("Aus gus", Rank.UNRANKED), MatchingMode.STRICT, false));
    // a misapplied query keeps them
    var q = query("Aus gus", Rank.SPECIES);
    q.setStatus(TaxonomicStatus.MISAPPLIED);
    assertUnresolved(merge(q));
  }

  @Test
  public void spellingIgnoresSubgenus() throws Exception {
    // the query tells nothing about a subgenus, so the accepted name is spelled like it
    assertMatch("hus1", merge(query("Aus hus", Rank.SPECIES)));
    // one that names it still prefers it
    assertMatch("hus1", merge(query("Aus (Aus) hus", Rank.SPECIES)));
    assertEquals("Aus hus", UsageMatcher.withoutSubgenus("Aus (Aus) hus"));
    assertEquals("Aus hus var. cus", UsageMatcher.withoutSubgenus("Aus (Bus) hus var. cus"));
    assertEquals("Aus hus", UsageMatcher.withoutSubgenus("Aus hus"));
    assertEquals("Aus", UsageMatcher.withoutSubgenus("Aus"));
    // a subgenus is no genus
    assertEquals("Aus (Aus)", UsageMatcher.withoutSubgenus("Aus (Aus)"));
    assertMatch("aus", merge(query("Aus", Rank.UNRANKED)));
    assertNull(UsageMatcher.withoutSubgenus(null));
  }

  @Test
  public void synonymsOfOneAccepted() throws Exception {
    // without the provisional copy the homonyms are synonyms of one accepted name
    assertMatch("ius1", merge(query("Aus ius", Rank.SPECIES)));
  }
}
