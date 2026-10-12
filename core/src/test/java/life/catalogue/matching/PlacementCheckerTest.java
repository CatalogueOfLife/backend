package life.catalogue.matching;

import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.NomCode;
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
 * The placement check and the matcher's refusal to guess between homonyms of disparate groups,
 * both against a small reference with genus homonyms. See https://github.com/CatalogueOfLife/backend/issues/1608
 */
public class PlacementCheckerTest {
  // canonical names index ids of the genera
  static final Map<String, Integer> NIDX = Map.of("Platygaster", 10, "Curvularia", 20, "Paracoccus", 30, "Novus", 40);
  UsageMatcher matcher;
  PlacementChecker checker;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "an", "Animalia", Rank.KINGDOM, null, null);
    add(store, "in", "Insecta", Rank.CLASS, "an", null);
    add(store, "hy", "Hymenoptera", Rank.ORDER, "in", null);
    add(store, "di", "Diptera", Rank.ORDER, "in", null);
    add(store, "he", "Hemiptera", Rank.ORDER, "in", null);
    add(store, "pl", "Plantae", Rank.KINGDOM, null, null);
    add(store, "fu", "Fungi", Rank.KINGDOM, null, null);
    add(store, "ba", "Bacteria", Rank.KINGDOM, null, null);
    add(store, "plat1", "Platygaster", Rank.GENUS, "hy", "Latreille, 1809");
    add(store, "plat2", "Platygaster", Rank.GENUS, "di", null);
    add(store, "curv", "Curvularia", Rank.GENUS, "fu", "Boedijn, 1933");
    add(store, "para1", "Paracoccus", Rank.GENUS, "he", "Ezzat & McConnell, 1956");
    add(store, "para2", "Paracoccus", Rank.GENUS, "ba", "Davis, 1969");

    var ni = mock(NameIndex.class);
    when(ni.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      SimpleName sn = inv.getArgument(0);
      var nidx = NIDX.get(sn.getName());
      return nidx == null ? NameMatch.noMatch() : NameMatch.match(nidx);
    });
    matcher = new UsageMatcher(1, ni, store, false);
    checker = new PlacementChecker(matcher, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, Rank rank, String parent, String authorship) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setParent(parent);
    sn.setAuthorship(authorship);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    sn.setNamesIndexId(NIDX.get(name));
    store.add(sn);
  }

  static SimpleName genus(String name, String authorship) {
    var sn = new SimpleName(null, name, authorship, Rank.GENUS);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    return sn;
  }

  static List<SimpleName> cl(String... names) {
    return java.util.Arrays.stream(names).map(n -> new SimpleName(null, n, Rank.UNRANKED)).toList();
  }

  @Test
  public void fungusUnderItsHostIsAConflict() {
    var conflict = checker.check(genus("Curvularia", null), cl("Plantae", "Poaceae"));
    assertTrue(conflict.isPresent());
    assertTrue(conflict.get().group().isDisparateTo(TaxGroup.Fungi));
    assertEquals(java.util.Set.of(TaxGroup.Fungi), conflict.get().referenceGroups());
  }

  @Test
  public void placementInAKnownGroupIsNoConflict() {
    assertTrue(checker.check(genus("Curvularia", null), cl("Fungi")).isEmpty());
    // the reference knows a Platygaster in the flies too
    assertTrue(checker.check(genus("Platygaster", null), cl("Animalia", "Diptera")).isEmpty());
  }

  @Test
  public void anyKnownHomonymIsNoConflict() {
    // a fungus Platygaster contradicts both the wasp and the midge
    assertTrue(checker.check(genus("Platygaster", null), cl("Fungi")).isPresent());
    // but the bacterium Paracoccus is known
    assertTrue(checker.check(genus("Paracoccus", null), cl("Bacteria")).isEmpty());
  }

  @Test
  public void coarseOrMissingEvidenceIsNoConflict() {
    // no classification and no code tells no group
    assertTrue(checker.check(genus("Curvularia", null), List.of()).isEmpty());
    // the zoological code only tells eukaryotes
    var zoo = genus("Curvularia", null);
    zoo.setCode(NomCode.ZOOLOGICAL);
    assertTrue(checker.check(zoo, List.of()).isEmpty());
    // a genus the reference does not know is new, not misplaced
    assertTrue(checker.check(genus("Novus", null), cl("Plantae")).isEmpty());
    assertTrue(checker.check(genus("Ignotus", null), cl("Plantae")).isEmpty());
  }

  @Test
  public void differentAuthorshipIsAHomonymTheReferenceLacks() {
    assertTrue(checker.check(genus("Curvularia", "Smith, 2020"), cl("Plantae", "Poaceae")).isEmpty());
    // the same author still conflicts
    assertTrue(checker.check(genus("Curvularia", "Boedijn, 1933"), cl("Plantae", "Poaceae")).isPresent());
  }

  @Test
  public void onlyAcceptedGeneraAreChecked() {
    var syn = genus("Curvularia", null);
    syn.setStatus(TaxonomicStatus.SYNONYM);
    assertTrue(checker.check(syn, cl("Plantae", "Poaceae")).isEmpty());

    var family = new SimpleName(null, "Curvularia", Rank.FAMILY);
    family.setStatus(TaxonomicStatus.ACCEPTED);
    assertTrue(checker.check(family, cl("Plantae", "Poaceae")).isEmpty());
  }

  static SimpleNameClassified<SimpleNameCached> query(String genus, String... classification) {
    var snc = new SimpleNameClassified<SimpleNameCached>(new SimpleNameCached(null, genus, Rank.GENUS));
    snc.setStatus(TaxonomicStatus.ACCEPTED);
    snc.setNamesIndexId(NIDX.get(genus));
    snc.setClassification(java.util.Arrays.stream(classification).map(n -> new SimpleNameCached(null, n, Rank.UNRANKED)).toList());
    return snc;
  }

  @Test
  public void matcherDoesNotGuessBetweenDisparateHomonyms() {
    // a genus with nothing but its name, like the implicit genus of a ZooBank species
    var m = matcher.match(query("Platygaster"));
    assertFalse(m.isMatch());
    assertTrue(m.unresolvedHomonym);

    m = matcher.match(query("Paracoccus"));
    assertFalse(m.isMatch());
    assertTrue(m.unresolvedHomonym);

    // a single genus of that name is no homonym question
    m = matcher.match(query("Curvularia"));
    assertTrue(m.isMatch());
    assertEquals("curv", m.usage.getId());
  }

  @Test
  public void groupEvidenceResolvesHomonyms() {
    // from the classification
    var m = matcher.match(query("Platygaster", "Hymenoptera"));
    assertEquals("plat1", m.usage.getId());

    // or from the fallback group set on the query, e.g. the group of the whole source dataset
    var q = query("Paracoccus");
    q.setGroup(TaxGroup.Bacteria);
    m = matcher.match(q);
    assertEquals("para2", m.usage.getId());

    // which never overrides a more specific classification
    q = query("Platygaster", "Diptera");
    q.setGroup(TaxGroup.Insects);
    m = matcher.match(q);
    assertEquals("plat2", m.usage.getId());
  }
}
