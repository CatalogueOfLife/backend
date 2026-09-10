package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Datasets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class ArchivedNameUsageMatchMapperTest extends MapperTestBase<ArchivedNameUsageMatchMapper> {

  public ArchivedNameUsageMatchMapperTest() {
    super(ArchivedNameUsageMatchMapper.class);
  }


  @Test
  public void get() throws Exception {
    mapper().get(DSID.of(appleKey, "xxx"));
  }

  @Test
  public void getCanonicalNidx() throws Exception {
    mapper().getCanonicalNidx(DSID.of(appleKey, "xxx"));
  }

  @Test
  public void deleteOrphaned() throws Exception {
    // no real data to delete but tests valid SQL
    mapper().deleteOrphans(appleKey);
  }

  @Test
  public void processIndexIds() throws Exception {
    mapper().processIndexIds(appleKey).forEach(System.out::println);
  }

  @Test
  public void createMissingUsages() throws Exception {
    mapper().createMissingMatches(Datasets.COL, 1000);
    mapper().createAllMatches();
  }

  @Test
  public void persist() throws Exception {
    var key = DSID.of(appleKey, "xxx");
    // creates a new match, then updates it. The apple names index has entries 1-4
    mapper().persist(key, null, 1);
    assertEquals((Integer) 1, mapper().get(key).getNidx());
    mapper().persist(key, null, 2);
    assertEquals((Integer) 2, mapper().get(key).getNidx());

    // no empty matches are stored
    mapper().persist(key, null, null);
    assertNull(mapper().get(key));
  }

}
