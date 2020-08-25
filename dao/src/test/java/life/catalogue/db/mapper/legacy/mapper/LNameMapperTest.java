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
import static org.junit.Assert.assertNull;

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
  @Ignore("current work")
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
    assertEquals("Chromis (Chromis) agilis", n.getName());
    assertEquals("<i>Chromis (Chromis) agilis</i> Smith, 1960", n.getNameHtml());
    assertEquals("Chromis", n.getGenus());
    assertEquals("Chromis", n.getSubgenus());
    assertEquals("agilis", n.getSpecies());
    assertNull(n.getInfraspecies());
    assertNull(n.getInfraspeciesMarker());
    assertEquals("Smith, 1960", n.getAuthor());
    assertEquals("http://fish.org/100", n.getOnlineResource());

    assertEquals("Fishes", n.getSourceDatabase());
    assertEquals("http://fish.org", n.getSourceDatabaseUrl());
    assertEquals("", n.getRecordScrutinyDate());

    //assertNull(n.getBibliographicCitation());
    //assertEquals(Rank.SPECIES, n.getChildTaxa());
    //assertEquals(Rank.SPECIES, n.getClassification());
    //assertEquals(Rank.SPECIES, n.getCommonNames());
    //assertEquals(Rank.SPECIES, n.getDistribution());


    n = (LSpeciesName) mapper().get(true, datasetKey, "u102");
    assertEquals("u102", n.getId());
    assertEquals(Rank.SUBSPECIES, n.getRank());
    assertEquals("Chromis (Chromis) agilis pacifica", n.getName());
    assertEquals("<i>Chromis (Chromis) agilis pacifica</i> Smith, 1973", n.getNameHtml());
    assertEquals("Smith, 1973", n.getAuthor());
    assertEquals("Chromis", n.getGenus());
    assertEquals("Chromis", n.getSubgenus());
    assertEquals("agilis", n.getSpecies());
    assertEquals("pacifica", n.getInfraspecies());
    assertEquals("subsp.", n.getInfraspeciesMarker());
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