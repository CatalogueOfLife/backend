package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameIndexEntry;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class NameMatchMapperTest extends MapperTestBase<NameMatchMapper> {

  private int datasetKey;

  public NameMatchMapperTest() {
    super(NameMatchMapper.class);
  }
  
  @Before
  public void initMappers() {
    datasetKey = testDataRule.testData.key;
  }

  @Test
  public void get() throws Exception {
    mapper().get(DSID.of(datasetKey, "1"));
  }

  @Test
  public void getCanonicalNidx() throws Exception {
    mapper().getCanonicalNidx(DSID.of(appleKey, "1"));
  }

  @Test
  public void copyDataset() throws Exception {
    // we also need other entities to not validate constraints
    CopyDatasetTestComponent.copy(mapper(VerbatimRecordMapper.class), datasetKey, false);
    CopyDatasetTestComponent.copy(mapper(ReferenceMapper.class), datasetKey, false);
    CopyDatasetTestComponent.copy(mapper(NameMapper.class), datasetKey, false);
    // now do the real copy
    CopyDatasetTestComponent.copy(mapper(), datasetKey, true);
  }

  @Test
  public void deleteOrphaned() throws Exception {
    // no real data to delete but tests valid SQL
    mapper().deleteOrphans(datasetKey);
  }

  @Test
  public void processIndexIds() throws Exception {
    mapper().processIndexIds(datasetKey, null).forEach(System.out::println);
  }

  @Test
  public void updateMatches() throws Exception {
    NameMapper nm = mapper(NameMapper.class);
    Integer nidx = 1;
    var cnt = mapper().update(NAME1, nidx);
    Name n = nm.get(NAME1);
    assertEquals(1, cnt);
    assertEquals(nidx, n.getNamesIndexId());

    // try to update a non existing name
    cnt = mapper().update(DSID.of(NAME1.getDatasetKey(), "2345678sedrftzh"), nidx);
    assertEquals(0, cnt);

    NameIndexEntry in = new NameIndexEntry();
    in.setScientificName(TestEntityGenerator.NAME4.getScientificName());
    // NAME4 ("Larus erfundus") normalizes to "larus erfund", the same bucket as the apple fixture's
    // id=4 row ("Larus erfundus" -> "larus erfund") - this raw insert bypasses the single-tier reuse
    // logic in NameIndexImpl, so give it its own distinct normalized literal to satisfy the unique
    // index rather than colliding with that fixture row.
    in.setNormalized("larus fusca-test");
    mapper(NamesIndexMapper.class).create(in);
    nidx = in.getKey();

    mapper().update(NAME1, nidx);
    n = nm.get(NAME1);
    assertEquals(nidx, n.getNamesIndexId());

    mapper().delete(NAME1);
    n = nm.get(NAME1);
    assertNull(n.getNamesIndexId());
  }
  
}
