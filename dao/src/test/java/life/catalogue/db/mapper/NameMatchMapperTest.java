package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.MatchType;
import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 */
public class NameMatchMapperTest extends MapperTestBase<NameMatchMapper> {

  private NameMatchMapper nameMapper;
  private int datasetKey;

  public NameMatchMapperTest() {
    super(NameMatchMapper.class);
  }
  
  @Before
  public void initMappers() {
    nameMapper = testDataRule.getMapper(NameMatchMapper.class);
    datasetKey = testDataRule.testData.key;
  }
  
  static Name create(final String id, final Name basionym) throws Exception {
    Name n = TestEntityGenerator.newName(id);
    n.setHomotypicNameId(basionym.getId());
    return n;
  }

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), datasetKey, true);
  }

  @Test
  public void deleteOrphaned() throws Exception {
    // no real data to delete but tests valid SQL
    mapper().deleteOrphaned(datasetKey, 1);
    mapper().deleteOrphaned(datasetKey, null);
  }

  @Test
  public void processIndexIds() throws Exception {
    mapper().processIndexIds(datasetKey, null).forEach(System.out::println);
  }

  @Test
  public void updateMatches() throws Exception {
    NameMapper nm = mapper(NameMapper.class);
    Integer nidx = 1;
    mapper().update(NAME1, nidx, MatchType.EXACT);
    NameMapper.NameWithNidx n = nm.getWithNidx(NAME1);
    assertEquals(MatchType.EXACT, n.namesIndexType);
    assertEquals(nidx, n.namesIndexId);

    IndexName in = new IndexName(TestEntityGenerator.NAME4);
    mapper(NamesIndexMapper.class).create(in);
    nidx = in.getKey();
    
    mapper().update(NAME1, nidx, MatchType.CANONICAL);
    n = nm.getWithNidx(NAME1);
    assertEquals(MatchType.CANONICAL, n.namesIndexType);
    assertEquals(nidx, n.namesIndexId);

    mapper().delete(NAME1);
    n = nm.getWithNidx(NAME1);
    assertEquals(MatchType.NONE, n.namesIndexType);
    assertNull(n.namesIndexId);
  }

  private static Name newAcceptedName(String scientificName) {
    return newName(DATASET11.getKey(), scientificName.toLowerCase().replace(' ', '-'), scientificName);
  }
  
}
