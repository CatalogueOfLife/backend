package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
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
    Integer ints = 1;
    mapper().update(datasetKey, NAME1.getId(), ints, MatchType.EXACT);
    NameMapper.NameWithNidx n = nm.getWithNidx(NAME1);
    assertEquals(MatchType.EXACT, n.namesIndexType);
    assertEquals(ints, n.namesIndexId);

    ints= 42213;
    mapper().update(datasetKey, NAME1.getId(), ints, MatchType.CANONICAL);
    n = nm.getWithNidx(NAME1);
    assertEquals(MatchType.CANONICAL, n.namesIndexType);
    assertEquals(ints, n.namesIndexId);

    ints = null;
    mapper().update(datasetKey, NAME1.getId(), ints, MatchType.NONE);
    n = nm.getWithNidx(NAME1);
    assertEquals(MatchType.NONE, n.namesIndexType);
    assertEquals(ints, n.namesIndexId);

    mapper().update(datasetKey, NAME1.getId(), null, null);
    n = nm.getWithNidx(NAME1);
    assertNull(n.namesIndexType);
    assertEquals(ints, n.namesIndexId);
  }

  private static Name newAcceptedName(String scientificName) {
    return newName(DATASET11.getKey(), scientificName.toLowerCase().replace(' ', '-'), scientificName);
  }
  
}
