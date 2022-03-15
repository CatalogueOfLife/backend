package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static life.catalogue.api.vocab.Datasets.COL;
import static org.junit.Assert.assertEquals;

public class SectorImportMapperTest extends MapperTestBase<SectorImportMapper> {
  static int attempts = 1;
  
  Sector s;
  Sector s2;

  @Before
  public void prepare() {
    attempts = 1;
    s = new Sector();
    s.setDatasetKey(COL);
    s.setSubjectDatasetKey(DATASET11.getKey());
    s.setMode(Sector.Mode.ATTACH);
    s.setSubject(newSimpleName());
    s.setTarget(newSimpleName());
    s.setNote(RandomUtils.randomUnicodeString(1024));
    s.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
    
    mapper(SectorMapper.class).create(s);
  
    s2 = new Sector();
    s2.setDatasetKey(COL);
    s2.setSubjectDatasetKey(DATASET12.getKey());
    s2.setMode(Sector.Mode.ATTACH);
    s2.setSubject(newSimpleName());
    s2.setTarget(newSimpleName());
    s2.setNote(RandomUtils.randomUnicodeString(1024));
    s2.setCreatedBy(TestEntityGenerator.USER_EDITOR.getKey());
    s2.setModifiedBy(TestEntityGenerator.USER_EDITOR.getKey());
  
    mapper(SectorMapper.class).create(s2);
  }
  
  public static SectorImport create(ImportState state, Sector s) {
    SectorImport d = new SectorImport();
    DatasetImportMapperTest.fill(d, state);
    d.setJob("SectorImportTest");
    d.setDatasetKey(s.getDatasetKey());
    d.setSectorKey(s.getId());
    d.setAttempt(attempts++);
    d.setDatasetAttempt(73);
    d.addWarning("warning 1");
    d.addWarning("warning 2");
    return d;
  }

  public SectorImportMapperTest() {
    super(SectorImportMapper.class);
  }
  
  
  @Test
  public void roundtrip() throws Exception {
    SectorImport d1 = create(ImportState.FINISHED, s);
    mapper().create(d1);
    commit();
    assertEquals(1, d1.getAttempt());
  
    SectorImport d2 = mapper().get(d1.getSectorDSID(), d1.getAttempt());
    assertEquals(d1, d2);
  }
  
  @Test
  public void listCount() throws Exception {
    mapper().create(create(ImportState.FAILED, s));
    mapper().create(create(ImportState.FINISHED, s));
    mapper().create(create(ImportState.PREPARING, s));
    mapper().create(create(ImportState.FINISHED, s));
    mapper().create(create(ImportState.CANCELED, s));
    mapper().create(create(ImportState.INSERTING, s2));
    mapper().create(create(ImportState.FINISHED, s2));
    
    assertEquals(7, mapper().count(null, null, null, null));
    assertEquals(7, mapper().count(null, null,null, new ArrayList<>()));
    assertEquals(1, mapper().count(null, null,null, List.of(ImportState.FAILED)));
    assertEquals(3, mapper().count(null, null,null, List.of(ImportState.FINISHED)));
    assertEquals(2, mapper().count(null, null,null, List.of(ImportState.INSERTING, ImportState.PREPARING)));
    
    assertEquals(2, mapper().list(null, null,null, List.of(ImportState.INSERTING, ImportState.PREPARING), null, new Page()).size());
    assertEquals(0, mapper().list(null, 100,null, List.of(ImportState.INSERTING, ImportState.PREPARING), false, new Page()).size());
    assertEquals(0, mapper().list(null, null,null, List.of(ImportState.INSERTING, ImportState.PREPARING), true, new Page()).size());

    assertEquals(5, mapper().count(s.getId(), null, null, null));
    assertEquals(5, mapper().count(s.getId(), null, s.getSubjectDatasetKey(), null));
    assertEquals(5, mapper().count(s.getId(), COL, s.getSubjectDatasetKey(), null));
    assertEquals(0, mapper().count(s2.getId(), COL, s.getSubjectDatasetKey(), null));
    assertEquals(5, mapper().count(null, COL, s.getSubjectDatasetKey(), null));
    assertEquals(2, mapper().count(null, COL, s2.getSubjectDatasetKey(), null));
    assertEquals(2, mapper().count(s2.getId(), COL, null, null));
    
    assertEquals(0, mapper().count(99999, null, null, null));
    assertEquals(0, mapper().count(99999, null, 789, null));
    assertEquals(0, mapper().count(null, 456789876, null, null));
    
  }

  @Test
  public void deleteByDataset() throws Exception {
    mapper().deleteByDataset(Datasets.COL);
  }

  @Test
  public void counts() throws Exception {
    assertEquals((Integer) 0, mapper().countBareName(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countDistribution(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countMedia(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countName(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countReference(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countSynonym(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countTaxon(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countTreatment(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countTypeMaterial(DATASET11.getKey(), 1));
    assertEquals((Integer) 0, mapper().countVernacular(DATASET11.getKey(), 1));
  }

  @Test
  public void countByMaps() throws Exception {
    assertEquals(0, mapper().countDistributionsByGazetteer(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countExtinctTaxaByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countIssues(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countMediaByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNameRelationsByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countUsagesByOrigin(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNamesByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNamesByStatus(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countNamesByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countSpeciesInteractionsByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countSynonymsByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countTaxaByRank(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countTaxonConceptRelationsByType(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countTypeMaterialByStatus(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countUsagesByStatus(DATASET11.getKey(), 1).size());
    assertEquals(0, mapper().countVernacularsByLanguage(DATASET11.getKey(), 1).size());
  }
}