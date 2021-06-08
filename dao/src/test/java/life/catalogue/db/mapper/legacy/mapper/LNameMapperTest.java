package life.catalogue.db.mapper.legacy.mapper;

import life.catalogue.db.LookupTables;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.MapperTestBase;
import life.catalogue.db.mapper.legacy.LNameMapper;
import life.catalogue.db.mapper.legacy.model.*;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class LNameMapperTest extends MapperTestBase<LNameMapper> {

  int datasetKey = TestDataRule.FISH.key;

  public LNameMapperTest() {
    super(LNameMapper.class, TestDataRule.fish());
  }

  @Before
  public void init () throws IOException, SQLException {
    LookupTables.recreateTables(session().getConnection());
  }

  @Test
  public void get() {
    LSpeciesName n = (LSpeciesName) mapper().get(datasetKey, "u100");
    assertEquals("u100", n.getId());
    assertEquals(Rank.SPECIES, n.getRank());

    LHigherName hn = (LHigherName) mapper().get(datasetKey, "u10");
    assertEquals("u10", hn.getId());
    assertEquals(Rank.GENUS, hn.getRank());

    n = (LSpeciesName) mapper().getFull(datasetKey, "u100");
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

    assertEquals("FishBase", n.getSourceDatabase());
    assertEquals("http://fishbase.de", n.getSourceDatabaseUrl());
    assertNull(n.getRecordScrutinyDate());

    //assertNull(n.getBibliographicCitation());
    assertEquals(1, n.getChildTaxa().size());
    n.getChildTaxa().forEach(this::assertPresent);

    assertEquals(5, n.getClassification().size());
    n.getClassification().forEach(this::assertPresent);

    assertEquals(4, n.getCommonNames().size());
    n.getCommonNames().forEach(this::assertPresent);

    //assertEquals(Rank.SPECIES, n.getDistribution());


    n = (LSpeciesName) mapper().getFull(datasetKey, "u102");
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

    // regular synonym
    n = (LSpeciesName) mapper().getFull(datasetKey, "us1");
    assertEquals("us1", n.getId());
    assertEquals(Rank.SPECIES, n.getRank());
    assertEquals("Chromis acuticeps", n.getName());
    assertEquals("Steindachner, 1866", n.getAuthor());
    assertEquals("<i>Chromis acuticeps</i> Steindachner, 1866", n.getNameHtml());
    assertEquals("Chromis", n.getGenus());
    assertNull(n.getSubgenus());
    assertEquals("acuticeps", n.getSpecies());
    assertNull(n.getInfraspecies());
    assertNull(n.getInfraspeciesMarker());

    // misapplied name
    n = (LSpeciesName) mapper().getFull(datasetKey, "u101");
    assertEquals("u101", n.getId());
    assertEquals(Rank.SPECIES, n.getRank());
    assertEquals("Chromis leucurus", n.getName());
    assertEquals("(non Gilbert, 1905)", n.getAuthor());
    assertEquals("<i>Chromis leucurus</i> (non Gilbert, 1905)", n.getNameHtml());
    assertEquals("Chromis", n.getGenus());
    assertNull(n.getSubgenus());
    assertEquals("leucurus", n.getSpecies());
    assertNull(n.getInfraspecies());
    assertNull(n.getInfraspeciesMarker());

    // genus synonym
    LSynonym syn = (LSynonym) mapper().getFull(datasetKey, "u11");
    assertEquals("u11", syn.getId());
    assertEquals(Rank.GENUS, syn.getRank());
    assertEquals("Ayresia", syn.getName());
    assertEquals("Cooper, 1863", syn.getAuthor());
    assertNull(syn.getGenus());
    assertNull(syn.getSubgenus());
    assertNull(syn.getSpecies());
    assertNull(syn.getInfraspecies());
    assertNull(syn.getInfraspeciesMarker());
    var acc = syn.getAcceptedName();
    assertEquals("u10", acc.getId());
    assertEquals(Rank.GENUS, acc.getRank());
    assertEquals("Chromis", acc.getName());
    assertEquals("Cuvier", acc.getAuthor());
    assertNull(acc.getGenus());
    assertNull(acc.getSubgenus());
    assertNull(acc.getSpecies());
    assertNull(acc.getInfraspecies());
    assertNull(acc.getInfraspeciesMarker());
  }

  void assertPresent(LHigherName n){
    assertNotNull(n.getId());
    assertNotNull(n.getRank());
    assertNotNull(n.getName());
    assertNotNull(n.getStatus());
  }

  void assertPresent(LCommonName n){
    assertNotNull(n.getName());
    assertNotNull(n.getCountry());
    assertNotNull(n.getLanguage());
  }

  @Test
  public void count() {
    assertEquals(0, mapper().count(datasetKey, true, "Larus"));
    assertEquals(5, mapper().count(datasetKey, true, "chromis"));
    assertEquals(5, mapper().count(datasetKey, true, "Chromis"));
    assertEquals(1, mapper().count(datasetKey, false, "Chromis"));
    assertEquals(0, mapper().count(datasetKey, false, "agilis"));
    assertEquals(1, mapper().count(datasetKey, true, "Perciform"));
    assertEquals(1, mapper().count(datasetKey, true, "perciformes"));
  }

  @Test
  public void search() {
    mapper().search(datasetKey, false, "Larus" ,0 ,2).forEach(this::isSpecies);
    mapper().searchFull(datasetKey, false, "Larus" ,0 ,2).forEach(this::isSpecies);
  }

  LSpeciesName isSpecies(LName n) {
    assertEquals(LSpeciesName.class, n.getClass());
    return (LSpeciesName) n;
  }
}