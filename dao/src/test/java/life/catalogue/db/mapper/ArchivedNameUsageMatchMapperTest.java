package life.catalogue.db.mapper;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Datasets;

import org.junit.Test;

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

}
