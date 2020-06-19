package life.catalogue.db.mapper.legacy.mapper;

import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.legacy.LNameMapper;
import life.catalogue.db.mapper.legacy.model.LName;
import life.catalogue.db.mapper.legacy.model.LSpeciesName;
import life.catalogue.db.mapper.MapperTestBase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LNameMapperTest extends MapperTestBase<LNameMapper> {

  int datasetKey = TestDataRule.TestData.APPLE.key;

  public LNameMapperTest() {
    super(LNameMapper.class);
  }

  @Test
  public void get() {
    LName n = mapper().get(datasetKey, "root-2");
    assertEquals(LSpeciesName.class, n.getClass());
  }

  @Test
  public void count() {
    // Apia apis
    // Malus sylvestris
    // Larus fuscus
    // Larus fusca
    // Larus erfundus
    assertEquals(3, mapper().count(datasetKey, true, "Larus"));
    assertEquals(3, mapper().count(datasetKey, true, "larus"));
    assertEquals(0, mapper().count(datasetKey, false, "Larus"));
    assertEquals(1, mapper().count(datasetKey, false, "Larus fusca"));
    assertEquals(2, mapper().count(datasetKey, true, "Larus fusc"));
    assertEquals(0, mapper().count(datasetKey, true, "fusc"));
  }

  @Test
  public void search() {
    mapper().search(datasetKey, false, "Larus" ,0 ,2).forEach(this::isSpecies);
  }

  void isSpecies(LName n) {
    assertEquals(LSpeciesName.class, n.getClass());
  }
}