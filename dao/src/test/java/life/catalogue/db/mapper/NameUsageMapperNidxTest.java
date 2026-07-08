package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.SimpleNameInDataset;
import life.catalogue.junit.TestDataRule;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
    // canonical "Abies alba" (index id 2) is shared by every usage across all three datasets
    var res = mapper().listByNamesIndexIDGlobal( 2, new Page());
    assertEquals(4, res.size());
    Set<DSID<String>> usageIDs = res.stream().map(DSID::copy).collect(Collectors.toSet());
    assertEquals(
      Set.of(DSID.of(100, "u1"), DSID.of(101, "u1"), DSID.of(102, "u1x"), DSID.of(102, "u2x")),
      usageIDs
    );

    // canonical "Abies" (index id 1) is not matched by any name -> no usages
    assertEquals(0, mapper().listByNamesIndexIDGlobal( 1, new Page()).size());

    // unknown nidx -> none
    assertEquals(0, mapper().listByNamesIndexIDGlobal( 99, new Page()).size());
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

}
