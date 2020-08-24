package life.catalogue.db.mapper.legacy.mapper;

import life.catalogue.db.LookupTables;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.db.mapper.legacy.LNameMapper;
import life.catalogue.db.mapper.legacy.model.LHigherName;
import life.catalogue.db.mapper.legacy.model.LName;
import life.catalogue.db.mapper.legacy.model.LSpeciesName;
import org.gbif.nameparser.api.Rank;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;

public class LNameMapperTest extends MapperTestBase<LNameMapper> {

  int datasetKey = TestDataRule.TestData.FISH.key;

  public LNameMapperTest() {
    super(LNameMapper.class, TestDataRule.fish());
  }

  @Before
  public void init () throws IOException, SQLException {
    LookupTables.recreateTables(session().getConnection());
  }

  @Test
  public void get() {
    LSpeciesName n = (LSpeciesName) mapper().get(false, datasetKey, "u100");
    assertEquals("u100", n.getId());
    assertEquals(Rank.SPECIES, n.getRank());

    LHigherName hn = (LHigherName) mapper().get(false, datasetKey, "u10");
    assertEquals("u10", hn.getId());
    assertEquals(Rank.GENUS, hn.getRank());

    n = (LSpeciesName) mapper().get(true, datasetKey, "u100");
    assertEquals("u100", n.getId());
    assertEquals(Rank.SPECIES, n.getRank());

  }

  @Test
  @Ignore
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
    mapper().search(false, datasetKey, false, "Larus" ,0 ,2).forEach(this::isSpecies);
    mapper().search(true, datasetKey, false, "Larus" ,0 ,2).forEach(this::isSpecies);
  }

  LSpeciesName isSpecies(LName n) {
    assertEquals(LSpeciesName.class, n.getClass());
    return (LSpeciesName) n;
  }
}