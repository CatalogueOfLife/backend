package life.catalogue.resources.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.MatchingConfig;
import life.catalogue.matching.UsageMatchWithOriginal;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherMemStore;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.Rank;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * /match/nameusage builds the name from its parts and honours the matching mode.
 */
public class FixedNameUsageMatchingResourceTest {
  static final Map<String, Integer> NIDX = Map.of("Plantae", 1, "Pinaceae", 2, "Abies", 3, "Abies alba", 4);
  FixedNameUsageMatchingResource resource;

  static SimpleNameCached usage(String id, String parent, Rank rank, String name, String authorship) {
    var u = new SimpleNameCached(id, name, rank);
    u.setParent(parent);
    u.setStatus(TaxonomicStatus.ACCEPTED);
    u.setAuthorship(authorship);
    u.setNamesIndexId(NIDX.get(name));
    return u;
  }

  @Before
  public void init() {
    NameIndex nidx = mock(NameIndex.class);
    when(nidx.match(any(Name.class), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
      Name n = inv.getArgument(0);
      Integer id = NIDX.get(n.getScientificName());
      return id == null ? NameMatch.noMatch() : NameMatch.match(id);
    });
    when(nidx.match(any(SimpleName.class), anyBoolean(), anyBoolean())).thenCallRealMethod();

    var store = new UsageMatcherMemStore(3);
    store.add(usage("P", null, Rank.KINGDOM, "Plantae", null));
    store.add(usage("F", "P", Rank.FAMILY, "Pinaceae", null));
    store.add(usage("G", "F", Rank.GENUS, "Abies", "Mill."));
    store.add(usage("S", "G", Rank.SPECIES, "Abies alba", "Mill."));

    var d = new Dataset();
    d.setKey(3);
    resource = new FixedNameUsageMatchingResource(new MatchingConfig(), d, new UsageMatcher(3, nidx, store, true));
  }

  UsageMatchWithOriginal match(String sciname, String genericName, String specificEpithet, MatchingMode mode, Classification cl) throws Exception {
    return resource.match(3, null, null, null, sciname, null, null, null, null, genericName, specificEpithet, null, mode, false, cl);
  }

  @Test
  public void nameFromParts() throws Exception {
    var m = match(null, "Abies", "alba", null, new Classification());
    assertEquals("S", m.usage.getId());
    // the label lacks the authorship of Abies alba Mill.
    assertEquals(MatchType.VARIANT, m.type);

    var cl = new Classification();
    cl.setGenus("Abies");
    // an abbreviated genus
    m = match("A. alba", null, null, null, cl);
    assertEquals("S", m.usage.getId());
    // only the epithet, which is also the species of the classification
    cl.setSpecies("alba");
    m = match("alba", null, null, null, cl);
    assertEquals("S", m.usage.getId());
  }

  @Test
  public void classificationOnly() throws Exception {
    var cl = new Classification();
    cl.setKingdom("Plantae");
    cl.setFamily("Pinaceae");
    var m = match(null, null, null, null, cl);
    assertEquals("F", m.usage.getId());
  }

  @Test(expected = IllegalArgumentException.class)
  public void nothing() throws Exception {
    match(null, null, null, null, new Classification());
  }

  @Test
  public void modes() throws Exception {
    var m = match("Abies nordmanniana", null, null, null, new Classification());
    assertEquals("G", m.usage.getId());
    assertEquals(MatchType.HIGHERRANK, m.type);

    m = match("Abies nordmanniana", null, null, MatchingMode.DEFAULT, new Classification());
    assertEquals("G", m.usage.getId());

    // strict never falls back to a higher rank
    m = match("Abies nordmanniana", null, null, MatchingMode.STRICT, new Classification());
    assertFalse(m.isMatch());

    assertThrows(IllegalArgumentException.class, () -> match("Abies alba", null, null, MatchingMode.FUZZY, new Classification()));
  }
}
