package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameIndexEntry;
import life.catalogue.api.model.Page;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Exercises the nidx-grouping queries against a single-tier names index fixture (test-data/nidx).
 * <p>
 * Post-Phase-1 the index is canonical-only: every names_index row is canonical (rank UNRANKED-style,
 * no authorship) with canonical_id == id, and every name_match.index_id points straight at such a
 * canonical row. The fixture reflects that: the single canonical "Abies alba" (index id 2) is the
 * match target of all four usages (dataset 100 u1, 101 u1, 102 u1x, 102 u2x), whose source names are
 * the authorship variants "Abies alba", "Abies alba Miller" and "Abies alba Mill.". A second
 * canonical "Abies" (index id 1) exists but is unreferenced, giving a nidx with no usages to assert.
 */
public class NameUsageMapperNidxTest extends MapperTestBase<NameUsageMapper> {

  public NameUsageMapperNidxTest() {
    super(NameUsageMapper.class, TestDataRule.nidx());
  }

  @Test
  public void listByNamesIndexIDGlobal() throws Exception {
    // canonical "Abies alba" (index id 2) is shared by every usage across all three datasets, but the
    // global query is public-filtered: dataset 100 (origin=PROJECT) is excluded, leaving the 3 external
    // usages (101/u1, 102/u1x, 102/u2x).
    var res = mapper().listByNamesIndexIDGlobal( 2, new Page());
    assertEquals(3, res.size());
    Set<DSID<String>> usageIDs = res.stream().map(DSID::copy).collect(Collectors.toSet());
    assertEquals(
      Set.of(DSID.of(101, "u1"), DSID.of(102, "u1x"), DSID.of(102, "u2x")),
      usageIDs
    );

    // canonical "Abies" (index id 1) is not matched by any name -> no usages
    assertEquals(0, mapper().listByNamesIndexIDGlobal( 1, new Page()).size());

    // unknown nidx -> none
    assertEquals(0, mapper().listByNamesIndexIDGlobal( 99, new Page()).size());

    // global count (datasetKey=null) is public-filtered too: it excludes the PROJECT dataset 100
    assertEquals(3, (int) mapper().countByNamesIndexID(2, null));
    assertEquals(0, (int) mapper().countByNamesIndexID(1, null));
    // per-dataset count is NOT public-filtered: the PROJECT dataset 100 still counts its own usage
    assertEquals(1, (int) mapper().countByNamesIndexID(2, 100));
    assertEquals(2, (int) mapper().countByNamesIndexID(2, 102));
  }

  @Test
  public void listByNamesIndexIDGlobalClassified() throws Exception {
    // single-tier registry: canonical "Abies alba" (index id 2) is matched by all four usages across
    // datasets 100 (origin=PROJECT, excluded), 101, 102 and 102. The classified query drops project
    // datasets, leaving the 3 external usages (101/u1, 102/u1x, 102/u2x).
    var res = mapper().listByNamesIndexIDGlobalClassified( 2, new Page());
    assertEquals(3, res.size());
    assertTrue(res.stream().noneMatch(u -> u.getDatasetKey() == 100));
    // canonical "Abies" (index id 1) is unreferenced -> none
    assertEquals(0, mapper().listByNamesIndexIDGlobalClassified( 1, new Page()).size());

    // the paging count must stay consistent with the filtered list, also excluding project usages
    assertEquals(3, (int) mapper().countByNamesIndexIDGlobalClassified(2));
    assertEquals(0, (int) mapper().countByNamesIndexIDGlobalClassified(1));
  }

  @Test
  public void listByCanonNIDX() throws Exception {
    // canonical "Abies alba" (index id 2) per dataset
    assertCanonNIDX(1, 100, 2);
    assertCanonNIDX(1, 101, 2);
    assertCanonNIDX(2, 102, 2);
    // not existing dataset
    assertCanonNIDX(0, 103, 2);
    // canonical "Abies" (index id 1) is unreferenced
    assertCanonNIDX(0, 100, 1);
  }

  void assertCanonNIDX(int expectedNum, int datasetKey, int canonNidx) {
    var res = mapper().listByCanonNIDX( datasetKey, canonNidx);
    assertEquals(expectedNum, res.size());
    for (var sn : res) {
      assertEquals((Integer)canonNidx, sn.getCanonicalId());
      assertNotNull(sn.getNamesIndexId());
      assertNotNull(sn.getNamesIndexMatchType());
      assertNotNull(sn.getRank());
      assertNotNull(sn.getName());
    }
  }

  /**
   * Verifies the intentional broadening of listRelated for the single-tier index: orig is
   * (100, u1) = "Abies alba Miller", matched to the shared canonical index id 2. Related usages are
   * now every usage matching that same canonical index_id (excluding orig itself), which pulls in the
   * authorship variants u1x ("Abies alba Miller") and u2x ("Abies alba Mill.") in dataset 102 as well
   * as the unauthored "Abies alba" u1 in dataset 101 - not just usages with orig's exact spelling.
   */
  @Test
  public void listRelated() throws Exception {
    var results = mapper().listRelated(DSID.of(100, "u1"), false, null, null, null, null, null);
    assertEquals(3, results.size());
    Set<DSID<String>> related = results.stream()
      .map(sn -> DSID.of(sn.getDatasetKey(), sn.getId()))
      .collect(Collectors.toSet());
    assertEquals(
      Set.of(DSID.of(101, "u1"), DSID.of(102, "u1x"), DSID.of(102, "u2x")),
      related
    );
  }

  /**
   * labelCounts groups name_match rows sharing one nidx by their rendered "scientificName [authorship]"
   * label and counts occurrences, across dataset boundaries, ordered by count desc.
   */
  @Test
  public void labelCounts() throws Exception {
    // a fresh canonical index entry, isolated from the shared "Abies alba" (nidx 2) fixture data
    NameIndexEntry entry = new NameIndexEntry();
    entry.setScientificName("Foo bar");
    entry.setNormalized("foo bar-labelcounts-test");
    mapper(NamesIndexMapper.class).create(entry);
    int nidx = entry.getKey();

    // two names in different datasets sharing the authored label "Foo bar Miller"
    Name n1 = TestEntityGenerator.newMinimalName(100, "lc1", "Foo bar", Rank.SPECIES);
    n1.setAuthorship("Miller");
    insertName(n1);

    Name n2 = TestEntityGenerator.newMinimalName(101, "lc1", "Foo bar", Rank.SPECIES);
    n2.setAuthorship("Miller");
    insertName(n2);

    // a third, unauthored name giving the distinct label "Foo bar"
    Name n3 = TestEntityGenerator.newMinimalName(102, "lc1", "Foo bar", Rank.SPECIES);
    n3.setAuthorship(null);
    insertName(n3);

    NameMatchMapper nmm = mapper(NameMatchMapper.class);
    nmm.create(DSID.of(100, "lc1"), null, nidx);
    nmm.create(DSID.of(101, "lc1"), null, nidx);
    nmm.create(DSID.of(102, "lc1"), null, nidx);
    commit();

    List<life.catalogue.api.model.LabelCount> counts = nmm.labelCounts(nidx);
    assertEquals(2, counts.size());
    assertEquals("Foo bar Miller", counts.get(0).getLabel());
    assertEquals(2, counts.get(0).getCount());
    assertEquals("Foo bar", counts.get(1).getLabel());
    assertEquals(1, counts.get(1).getCount());
  }

}
