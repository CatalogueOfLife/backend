package life.catalogue.matching;

import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.SimpleName;
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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A concrete rank of the same major group above genus does not veto a candidate, other ranks still do.
 * See https://github.com/CatalogueOfLife/backend/issues/1658 and https://github.com/CatalogueOfLife/backend/issues/1659
 */
public class UsageMatcherRankGroupTest {
  // canonical names index ids
  static final Map<String, Integer> NIDX = Map.of("Bacteria", 10, "Osteichthyes", 20, "Apini", 30, "Bombini", 40,
    "Pyrobombus", 50, "Chordata", 60);
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "bac", "Bacteria", Rank.DOMAIN, null);
    add(store, "an", "Animalia", Rank.KINGDOM, null);
    add(store, "ar", "Arthropoda", Rank.PHYLUM, "an");
    add(store, "in", "Insecta", Rank.CLASS, "ar");
    add(store, "hy", "Hymenoptera", Rank.ORDER, "in");
    // the stick insect genus Bacteria Berthold, 1827
    add(store, "bacg", "Bacteria", Rank.GENUS, "in");
    add(store, "ch", "Chordata", Rank.PHYLUM, "an");
    add(store, "os", "Osteichthyes", Rank.PARVPHYLUM, "ch");
    add(store, "api", "Apidae", Rank.FAMILY, "hy");
    add(store, "apit", "Apini", Rank.TRIBE, "api");
    add(store, "bomf", "Bombini", Rank.FAMILY, "hy");
    add(store, "boms", "Bombini", Rank.SUBFAMILY, "api");
    add(store, "pyr", "Pyrobombus", Rank.GENUS, "api");

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
    q.setNamesIndexId(NIDX.get(name));
    var snc = new SimpleNameClassified<SimpleNameCached>(q);
    snc.setClassification(Arrays.asList(classification));
    return matcher.match(snc, false, false, true);
  }

  static void assertMatch(String id, UsageMatch m) {
    assertTrue(m.isMatch());
    assertEquals(id, m.usage.getId());
  }

  @Test
  public void domains() throws Exception {
    assertMatch("bac", match("Bacteria", Rank.SUPERKINGDOM));
    assertMatch("bac", match("Bacteria", Rank.KINGDOM));
    assertMatch("bac", match("Bacteria", Rank.DOMAIN));
    // the genus stays out of reach of a higher rank
    assertMatch("bacg", match("Bacteria", Rank.GENUS));

    // a phylum missing from the reference falls back to the domain given as its kingdom
    var m = match("Proteobacteria", Rank.PHYLUM, new SimpleNameCached(null, "Bacteria", Rank.KINGDOM));
    assertMatch("bac", m);
    assertEquals(MatchType.HIGHERRANK, m.type);
  }

  @Test
  public void sameGroup() throws Exception {
    assertMatch("os", match("Osteichthyes", Rank.CLASS));
    assertMatch("os", match("Osteichthyes", Rank.SUPERCLASS));
    assertMatch("apit", match("Apini", Rank.SUBFAMILY));
    // the exact rank still wins
    assertMatch("bomf", match("Bombini", Rank.FAMILY));
    assertMatch("boms", match("Bombini", Rank.SUBFAMILY));
  }

  @Test
  public void otherGroupsStayStrict() throws Exception {
    // genus group
    assertFalse(match("Pyrobombus", Rank.SUBGENUS).isMatch());
    // family group vs ranks above family
    assertFalse(match("Apini", Rank.ORDER).isMatch());
    // above family vs genus
    assertFalse(match("Chordata", Rank.GENUS).isMatch());
  }

  @Test
  public void sameSupragenericGroup() {
    assertTrue(UsageMatcher.sameSupragenericGroup(Rank.DOMAIN, Rank.KINGDOM));
    assertTrue(UsageMatcher.sameSupragenericGroup(Rank.CLASS, Rank.PARVPHYLUM));
    assertTrue(UsageMatcher.sameSupragenericGroup(Rank.SUPERFAMILY, Rank.SUBTRIBE));
    assertFalse(UsageMatcher.sameSupragenericGroup(Rank.ORDER, Rank.SUPERFAMILY));
    assertFalse(UsageMatcher.sameSupragenericGroup(Rank.TRIBE, Rank.GENUS));
    assertFalse(UsageMatcher.sameSupragenericGroup(Rank.GENUS, Rank.SUBGENUS));
    assertFalse(UsageMatcher.sameSupragenericGroup(Rank.SPECIES, Rank.SUBSPECIES));
    assertFalse(UsageMatcher.sameSupragenericGroup(Rank.SUPRAGENERIC_NAME, Rank.FAMILY));
    assertFalse(UsageMatcher.sameSupragenericGroup(Rank.UNRANKED, Rank.FAMILY));
    assertFalse(UsageMatcher.sameSupragenericGroup(null, Rank.FAMILY));
  }
}
