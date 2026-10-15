package life.catalogue.matching;

import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
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
 * A query classified in another taxonomic group than a candidate, e.g. plankton filed under Animalia.
 * Merges and the STRICT mode keep the group veto, only an equal authorship of a bi- or trinomial overrules it.
 * The RELAXED mode treats such a conflict as a preference. See https://github.com/CatalogueOfLife/backend/issues/1650
 */
public class UsageMatcherGroupConflictTest {
  static final Map<String, Integer> NIDX = Map.of(
    "Tripos", 10, "Tripos furca", 11, "Bracon hebetor", 20, "Duplex simplex", 30, "Capillaria", 40, "Gryllus campestris", 50
  );
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "an", "Animalia", Rank.KINGDOM, null, null);
    add(store, "art", "Arthropoda", Rank.PHYLUM, "an", null);
    add(store, "ins", "Insecta", Rank.CLASS, "art", null);
    add(store, "hym", "Hymenoptera", Rank.ORDER, "ins", null);
    add(store, "bh", "Bracon hebetor", Rank.SPECIES, "hym", "Say, 1836");
    add(store, "dsa", "Duplex simplex", Rank.SPECIES, "hym", "Smith, 1900");
    add(store, "gc", "Gryllus campestris", Rank.SPECIES, "hym", "Linnaeus, 1758");

    add(store, "ch", "Chromista", Rank.KINGDOM, null, null);
    add(store, "mio", "Miozoa", Rank.PHYLUM, "ch", null);
    add(store, "tri", "Tripos", Rank.GENUS, "mio", "Bory de Saint-Vincent, 1823");
    add(store, "trf", "Tripos furca", Rank.SPECIES, "tri", "(Ehrenberg) F.Gómez, 2013");
    add(store, "dsp", "Duplex simplex", Rank.SPECIES, "mio", "Jones, 1950");
    // a misplaced duplicate of the cricket, written like the query
    add(store, "gcx", "Gryllus campestris", Rank.SPECIES, "mio", "Linnaeus 1758");

    add(store, "fu", "Fungi", Rank.KINGDOM, null, null);
    add(store, "asc", "Ascomycota", Rank.PHYLUM, "fu", null);
    add(store, "cap", "Capillaria", Rank.GENUS, "asc", "Pers.");

    var ni = mock(NameIndex.class);
    when(ni.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      SimpleName sn = inv.getArgument(0);
      var nidx = NIDX.get(sn.getName());
      return nidx == null ? NameMatch.noMatch() : NameMatch.match(nidx);
    });
    matcher = new UsageMatcher(1, ni, store, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, Rank rank, String parent, String authorship) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setParent(parent);
    sn.setAuthorship(authorship);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    sn.setNamesIndexId(NIDX.get(name));
    store.add(sn);
  }

  /**
   * A query with the rank the parser reads from the name, an optional authorship and a classification.
   * @param mode the read-only matching mode, or null for the match a merge does
   */
  UsageMatch match(MatchingMode mode, String name, String authorship, SimpleNameCached... classification) {
    var q = new SimpleNameCached(null, name, name.contains(" ") ? Rank.SPECIES : Rank.GENUS);
    q.setAuthorship(authorship);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    q.setNamesIndexId(NIDX.get(name));
    var snc = new SimpleNameClassified<SimpleNameCached>(q);
    snc.setClassification(Arrays.asList(classification));
    return mode == null ? matcher.match(snc, false, false) : matcher.match(snc, mode, false);
  }

  static SimpleNameCached kingdom(String name) {
    return new SimpleNameCached(null, name, Rank.KINGDOM);
  }

  static void assertMatch(String id, UsageMatch m) {
    assertTrue("expected " + id, m.isMatch());
    assertEquals(id, m.usage.getId());
  }

  @Test
  public void merges() {
    strict(null);
  }

  @Test
  public void strictMode() {
    strict(MatchingMode.STRICT);
  }

  void strict(MatchingMode mode) {
    // the classification contradicts and nothing else speaks for the protist
    assertFalse(match(mode, "Tripos furca", null, kingdom("Animalia")).isMatch());
    assertFalse(match(mode, "Tripos", null, kingdom("Animalia")).isMatch());
    // an equal authorship overrules the group of a bi- or trinomial, also when written differently
    assertMatch("trf", match(mode, "Tripos furca", "(Ehrenberg) F.Gómez, 2013", kingdom("Animalia")));
    assertMatch("trf", match(mode, "Tripos furca", "(Ehrenb.) F.Gómez, 2013", kingdom("Animalia")));
    // but not of a uninomial
    assertFalse(match(mode, "Tripos", "Bory de Saint-Vincent, 1823", kingdom("Animalia")).isMatch());
    // and never a different authorship
    assertFalse(match(mode, "Bracon hebetor", "L.", kingdom("Plantae")).isMatch());
    equalAuthorshipOnlyRescues(mode);
  }

  /**
   * An equal authorship only rescues a usage of another group if no usage of a compatible group is left,
   * so a misplaced duplicate never wins over the usage in place.
   */
  void equalAuthorshipOnlyRescues(MatchingMode mode) {
    var insects = new SimpleNameCached(null, "Insecta", Rank.CLASS);
    assertMatch("gc", match(mode, "Gryllus campestris", "Linnaeus 1758", kingdom("Animalia"), insects));
    assertMatch("gc", match(mode, "Gryllus campestris", "Linnaeus, 1758", kingdom("Animalia"), insects));
  }

  @Test
  public void relaxedBinomials() {
    assertMatch("trf", match(MatchingMode.RELAXED, "Tripos furca", null, kingdom("Animalia")));
    assertMatch("bh", match(MatchingMode.RELAXED, "Bracon hebetor", null, kingdom("Plantae")));
    // a different authorship is another name, whatever its group
    assertFalse(match(MatchingMode.RELAXED, "Bracon hebetor", "L.", kingdom("Plantae")).isMatch());
    // a specific group is evidence worth its veto, like Sabia parviflora given in the plant family Sabiaceae
    assertFalse(match(MatchingMode.RELAXED, "Bracon hebetor", null, kingdom("Plantae"), new SimpleNameCached(null, "Fabaceae", Rank.FAMILY)).isMatch());
    // a candidate of a compatible group wins over one of a conflicting group
    assertMatch("dsa", match(MatchingMode.RELAXED, "Duplex simplex", null, kingdom("Animalia")));
    assertMatch("dsp", match(MatchingMode.RELAXED, "Duplex simplex", null, kingdom("Chromista"), new SimpleNameCached(null, "Miozoa", Rank.PHYLUM)));
    equalAuthorshipOnlyRescues(MatchingMode.RELAXED);
  }

  @Test
  public void relaxedUninomials() {
    // protists overlap with animals
    assertMatch("tri", match(MatchingMode.RELAXED, "Tripos", null, kingdom("Animalia")));
    // the fungus Capillaria is no nematode, animals and fungi do not overlap
    assertFalse(match(MatchingMode.RELAXED, "Capillaria", null, kingdom("Animalia"), new SimpleNameCached(null, "Nematoda", Rank.PHYLUM)).isMatch());
    // plants overlap with fungi
    assertMatch("cap", match(MatchingMode.RELAXED, "Capillaria", null, kingdom("Plantae")));
  }
}
