package life.catalogue.resources.matching;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherMemStore;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.resources.matching.gbif.GbifNameUsageMatch;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GbifSpeciesMatchResourceTest {
  static final UUID CHECKLIST = UUID.fromString("7ddf754f-d193-4cc9-b351-99906754a03b");
  // canonical names index ids
  static final Map<String, Integer> NIDX = Map.of(
    "Plantae", 1,
    "Pinaceae", 2,
    "Abies", 3,
    "Abies alba", 4,
    "Abies pectinata", 5
  );
  GbifSpeciesMatchResource resource;

  static SimpleNameCached usage(String id, String parent, Rank rank, TaxonomicStatus status, String name, String authorship) {
    var u = new SimpleNameCached(id, name, rank);
    u.setParent(parent);
    u.setStatus(status);
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
    store.add(usage("P", null, Rank.KINGDOM, TaxonomicStatus.ACCEPTED, "Plantae", null));
    store.add(usage("F", "P", Rank.FAMILY, TaxonomicStatus.ACCEPTED, "Pinaceae", null));
    store.add(usage("G", "F", Rank.GENUS, TaxonomicStatus.ACCEPTED, "Abies", "Mill."));
    store.add(usage("S", "G", Rank.SPECIES, TaxonomicStatus.ACCEPTED, "Abies alba", "Mill."));
    store.add(usage("SYN", "S", Rank.SPECIES, TaxonomicStatus.SYNONYM, "Abies pectinata", "DC."));

    var d = new Dataset();
    d.setKey(3);
    d.setTitle("Firs");
    resource = new GbifSpeciesMatchResource(d, new UsageMatcher(3, nidx, store, true), CHECKLIST);
  }

  GbifNameUsageMatch match(String name, boolean strict) {
    return resource.match(null, null, null, name, null, null, null, null, null, null, null, null, null, strict, false, new Classification());
  }

  @Test
  public void exact() {
    var m = match("Abies alba Mill.", false);
    assertEquals("S", m.usage.key);
    assertEquals("EXACT", m.diagnostics.matchType);
    assertEquals(List.of("P", "F", "G", "S"), m.classification.stream().map(rn -> rn.key).toList());
  }

  @Test
  public void synonym() {
    var m = match("Abies pectinata", false);
    assertEquals("SYN", m.usage.key);
    assertTrue(m.synonym);
    assertEquals("S", m.acceptedUsage.key);
  }

  @Test
  public void strict() {
    var m = match("Abies nordmanniana", false);
    assertEquals("G", m.usage.key);
    assertEquals("HIGHERRANK", m.diagnostics.matchType);

    m = match("Abies nordmanniana", true);
    assertNull(m.usage);
    assertEquals("NONE", m.diagnostics.matchType);
  }

  @Test
  public void epithets() {
    assertEquals("Abies alba", GbifSpeciesMatchResource.joinEpithets("Abies", " alba ", null));
    assertNull(GbifSpeciesMatchResource.joinEpithets(null, " ", null));

    var m = resource.match(null, null, null, null, null, null, null, null, null, null, "Abies", "alba", null, false, false, new Classification());
    assertEquals("S", m.usage.key);
  }

  @Test
  public void gbifParamNames() {
    // GBIF's scientificNameAuthorship + taxonRank, the CLB names authorship + rank are aliases
    var m = resource.match(null, null, null, "Abies alba", "Mill.", null, "species", null, null, null, null, null, null, false, false, new Classification());
    assertEquals("S", m.usage.key);
    assertEquals("EXACT", m.diagnostics.matchType);

    m = resource.match(null, null, null, "Abies alba", null, "Mill.", null, "species", "genus", null, null, null, null, false, false, new Classification());
    assertEquals("S", m.usage.key);
    assertEquals("EXACT", m.diagnostics.matchType);

    // verbatimTaxonRank is used when taxonRank is missing: a genus rank does not fit the species name
    m = resource.match(null, null, null, "Abies alba", "Mill.", null, null, "genus", null, null, null, null, null, true, false, new Classification());
    assertNull(m.usage);
  }

  @Test
  public void usageKey() {
    var m = resource.match(null, "SYN", null, null, null, null, null, null, null, null, null, null, null, false, false, null);
    assertEquals("SYN", m.usage.key);
    assertEquals("EXACT", m.diagnostics.matchType);
    assertEquals("S", m.acceptedUsage.key);

    m = resource.match(null, null, "nope", null, null, null, null, null, null, null, null, null, null, false, false, null);
    assertNull(m.usage);
    assertEquals("NONE", m.diagnostics.matchType);
  }

  @Test
  public void checklistKey() {
    var m = resource.match(CHECKLIST, null, null, "Abies", null, null, null, null, null, null, null, null, null, false, false, new Classification());
    assertEquals("G", m.usage.key);
    assertThrows(IllegalArgumentException.class,
      () -> resource.match(UUID.randomUUID(), null, null, "Abies", null, null, null, null, null, null, null, null, null, false, false, new Classification())
    );
  }

  @Test(expected = IllegalArgumentException.class)
  public void missingName() {
    match(null, false);
  }

  @Test
  public void metadata() {
    var md = resource.metadata();
    assertEquals("3", md.mainIndex.clbDatasetKey);
    assertEquals(CHECKLIST.toString(), md.mainIndex.datasetKey);
    assertEquals("Firs", md.mainIndex.datasetTitle);
    assertEquals(5, (int) md.mainIndex.nameUsageCount);
  }
}
