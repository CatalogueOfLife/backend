package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.ArchivedNameUsage;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.vocab.MatchType;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.NAME1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class ArchivedNameUsageMatchMapperTest extends MapperTestBase<ArchivedNameUsageMatchMapper> {

  private int datasetKey;
  private ArchivedNameUsage u;
  IndexName nidx;

  public ArchivedNameUsageMatchMapperTest() {
    super(ArchivedNameUsageMatchMapper.class);
  }
  
  @Before
  public void initMappers() {
    datasetKey = testDataRule.testData.key;
    u = ArchivedNameMapperTest.create();
    mapper(ArchivedNameMapper.class).create(u);

    // add names index match
    var nim = mapper(NamesIndexMapper.class);
    nidx = new IndexName(u.getName());
    nim.create(nidx);

    var nmm = mapper(ArchivedNameUsageMatchMapper.class);
    nmm.create(DSID.of(3, u.getId()), nidx.getKey(), MatchType.EXACT);
    commit();
  }

  @Test
  public void get() throws Exception {
    mapper().get(DSID.of(datasetKey, u.getId()));
  }

  @Test
  public void deleteOrphaned() throws Exception {
    // no real data to delete but tests valid SQL
    mapper().deleteOrphaned(datasetKey);
  }

  @Test
  public void processIndexIds() throws Exception {
    mapper().processIndexIds(datasetKey).forEach(System.out::println);
  }

  @Test
  public void updateMatches() throws Exception {
    Integer nidx = 1;
    mapper().update(u, nidx, MatchType.EXACT);
    var n = mapper().get(u);
    assertEquals(MatchType.EXACT, n.getType());
    assertEquals(nidx, n.getName().getKey());
  }
  
}
