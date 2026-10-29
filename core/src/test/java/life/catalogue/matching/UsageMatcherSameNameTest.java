package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Picks among usages that share one canonical name: the query's own name in its classification and provisional
 * duplicates must not decide, an exact synonym still does. See https://github.com/CatalogueOfLife/backend/issues/1657
 */
public class UsageMatcherSameNameTest {
  // canonical names index ids
  static final Map<String, Integer> NIDX = Map.of("Magnoliopsida", 10, "Hemiptera", 20, "Limosa limosa islandica", 30,
    "Culex pipiens", 40);
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "pl", "Plantae", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add(store, "tr", "Tracheophyta", Rank.PHYLUM, "pl", null, TaxonomicStatus.ACCEPTED);
    add(store, "mg", "Magnoliopsida", Rank.CLASS, "tr", null, TaxonomicStatus.ACCEPTED);
    add(store, "ml", "Magnoliales", Rank.ORDER, "mg", null, TaxonomicStatus.ACCEPTED);
    add(store, "mgs", "Magnoliopsida", Rank.CLASS, "ml", "Brongn.", TaxonomicStatus.SYNONYM);

    add(store, "an", "Animalia", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add(store, "ar", "Arthropoda", Rank.PHYLUM, "an", null, TaxonomicStatus.ACCEPTED);
    add(store, "in", "Insecta", Rank.CLASS, "ar", null, TaxonomicStatus.ACCEPTED);
    add(store, "he", "Hemiptera", Rank.ORDER, "in", "Linnaeus, 1758", TaxonomicStatus.ACCEPTED);
    add(store, "mb", "Membracidae", Rank.FAMILY, "he", null, TaxonomicStatus.ACCEPTED);
    add(store, "en", "Enchenopa", Rank.GENUS, "mb", null, TaxonomicStatus.ACCEPTED);
    add(store, "heg", "Hemiptera", Rank.GENUS, "en", "Germar, 1833", TaxonomicStatus.SYNONYM);

    add(store, "ch", "Chordata", Rank.PHYLUM, "an", null, TaxonomicStatus.ACCEPTED);
    add(store, "li", "Limosa limosa", Rank.SPECIES, "ch", "(Linnaeus, 1758)", TaxonomicStatus.ACCEPTED);
    add(store, "lia", "Limosa limosa islandica", Rank.SUBSPECIES, "li", "C. L. Brehm, 1831", TaxonomicStatus.ACCEPTED);
    add(store, "lip", "Limosa limosa islandica", Rank.SUBSPECIES, "li", "C.L.Brehm, 1831", TaxonomicStatus.PROVISIONALLY_ACCEPTED);

    // the subgenus is no part of the canonical name
    add(store, "cx", "Culex (Culex) pipiens", Rank.SPECIES, "in", "Linnaeus, 1758", TaxonomicStatus.ACCEPTED, NIDX.get("Culex pipiens"));
    add(store, "cxs", "Culex pipiens", Rank.SPECIES, "cx", "Linnaeus, 1758", TaxonomicStatus.SYNONYM);

    matcher = new UsageMatcher(1, mock(NameIndex.class), store, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, Rank rank, String parent, String authorship, TaxonomicStatus status) {
    add(store, id, name, rank, parent, authorship, status, NIDX.get(name));
  }

  static void add(UsageMatcherMemStore store, String id, String name, Rank rank, String parent, String authorship, TaxonomicStatus status,
                  Integer nidx) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setParent(parent);
    sn.setAuthorship(authorship);
    sn.setStatus(status);
    sn.setNamesIndexId(nidx);
    store.add(sn);
  }

  static SimpleNameCached parent(String name, Rank rank) {
    return new SimpleNameCached(null, name, rank);
  }

  UsageMatch match(String name, String authorship, Rank rank, SimpleNameCached... classification) throws Exception {
    var q = new SimpleNameCached(null, name, rank);
    q.setAuthorship(authorship);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    q.setNamesIndexId(NIDX.get(name));
    var snc = new SimpleNameClassified<SimpleNameCached>(q);
    snc.setClassification(Arrays.asList(classification));
    return matcher.match(snc, false, false);
  }

  @Test
  public void ownNameInClassification() throws Exception {
    var m = match("Magnoliopsida", null, Rank.CLASS,
      parent("Magnoliopsida", Rank.CLASS), parent("Tracheophyta", Rank.PHYLUM), parent("Plantae", Rank.KINGDOM));
    assertEquals("mg", m.usage.getId());

    m = match("Hemiptera", null, Rank.UNRANKED,
      parent("Hemiptera", Rank.ORDER), parent("Insecta", Rank.CLASS), parent("Arthropoda", Rank.PHYLUM), parent("Animalia", Rank.KINGDOM));
    assertEquals("he", m.usage.getId());
  }

  @Test
  public void provisionalDuplicate() throws Exception {
    var m = match("Limosa limosa islandica", "C.L.Brehm, 1831", Rank.SUBSPECIES);
    assertEquals("lia", m.usage.getId());
    // an exact label of the accepted usage
    m = match("Limosa limosa islandica", "C. L. Brehm, 1831", Rank.SUBSPECIES);
    assertEquals("lia", m.usage.getId());
    assertEquals(MatchType.EXACT, m.type);
  }

  @Test
  public void exactSynonymStillWins() throws Exception {
    var m = match("Culex pipiens", "Linnaeus, 1758", Rank.SPECIES);
    assertEquals("cxs", m.usage.getId());
    assertEquals(MatchType.EXACT, m.type);
  }
}
