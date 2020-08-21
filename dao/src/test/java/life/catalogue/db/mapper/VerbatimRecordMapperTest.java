package life.catalogue.db.mapper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.PgSetupRule;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static life.catalogue.api.TestEntityGenerator.DATASET11;
import static life.catalogue.api.TestEntityGenerator.TAXON1;
import static life.catalogue.db.mapper.LogicalOperator.AND;
import static life.catalogue.db.mapper.LogicalOperator.OR;
import static org.junit.Assert.assertEquals;


public class VerbatimRecordMapperTest extends MapperTestBase<VerbatimRecordMapper> {
  private static long line = 1;
  private static final int datasetKey = DATASET11.getKey();
  
  public VerbatimRecordMapperTest() {
    super(VerbatimRecordMapper.class);
  }

  @Test
  public void copyDataset() throws Exception {
    Partitioner.partition(PgSetupRule.getSqlSessionFactory(), 999);
    mapper().copyDataset(datasetKey, 999);
  }

  @Test
  public void roundtrip() {
    VerbatimRecord r1 = TestEntityGenerator.createVerbatim();
    mapper().create(r1);
    
    commit();
    
    VerbatimRecord r2 = mapper().get(r1);
    
    assertEquals(r1, r2);
  }
  
  @Test
  public void list() {
    assertEquals(0, mapper().list(TAXON1.getDatasetKey(), null, null, AND,
        Lists.newArrayList(Issue.ACCEPTED_ID_INVALID),null,
        new Page()).size());
  
    assertEquals(1, mapper().list(TAXON1.getDatasetKey(), null, null, AND,
        Lists.newArrayList(Issue.ID_NOT_UNIQUE), null,
        new Page()).size());
  
    insertTestData();
  
    assertEquals(8, mapper().list(TAXON1.getDatasetKey(), null, null, AND,null, null, new Page()).size());
    assertEquals(2, mapper().list(TAXON1.getDatasetKey(), null, null, AND,null, "abies", new Page()).size());
    assertEquals(2, mapper().list(TAXON1.getDatasetKey(), null, null, AND,null, "t1", new Page()).size());
    assertEquals(1, mapper().list(TAXON1.getDatasetKey(), null, null, AND,null, "alpina", new Page()).size());
  }
  
  @Test
  public void count() {
    // count apples. rely on import metrics for quick counts so derive them first
    generateDatasetImport(DATASET11.getKey());
    assertEquals(5, mapper().count(datasetKey, null, null, AND,null, null));
    assertEquals(3, mapper().count(datasetKey, Lists.newArrayList(AcefTerm.AcceptedSpecies), null, AND,null, null));
    assertEquals(0, mapper().count(datasetKey, Lists.newArrayList(AcefTerm.AcceptedInfraSpecificTaxa), null, AND,null, null));
  
    insertTestData();
    assertEquals(8, mapper().count(datasetKey, null, null, AND,null, null));
    assertEquals(2, mapper().count(datasetKey, Lists.newArrayList(AcefTerm.AcceptedInfraSpecificTaxa), new HashMap<>(), AND, new ArrayList<>(), null));
    assertEquals(1, mapper().count(datasetKey, Lists.newArrayList(AcefTerm.AcceptedInfraSpecificTaxa), ImmutableMap.of(DwcTerm.genus, "Abies"), AND, null, null));
    assertEquals(2, mapper().count(datasetKey, null, ImmutableMap.of(DwcTerm.genus, "Abies"), AND, null, null));
    assertEquals(1, mapper().count(datasetKey, null, ImmutableMap.of(DwcTerm.genus, "Abies"), AND, Lists.newArrayList(Issue.BASIONYM_ID_INVALID), null));
    assertEquals(0, mapper().count(datasetKey, null, ImmutableMap.of(
        AcefTerm.InfraSpeciesEpithet, "alpina",
        AcefTerm.AcceptedTaxonID, "t1"
    ), AND, null, null));
    assertEquals(2, mapper().count(datasetKey, null, ImmutableMap.of(
        AcefTerm.InfraSpeciesEpithet, "alpina",
        AcefTerm.AcceptedTaxonID, "t1"
    ), OR, null, null));
    assertEquals(1, mapper().count(datasetKey, null, ImmutableMap.of(
        AcefTerm.InfraSpeciesEpithet, "alpina",
        AcefTerm.AcceptedTaxonID, "t2"
    ), AND, null, null));
    assertEquals(1, mapper().count(datasetKey, null, ImmutableMap.of(
        AcefTerm.InfraSpeciesEpithet, "alpina",
        AcefTerm.AcceptedTaxonID, "t2"
    ), OR, null, null));
  }
  
  private void insertTestData() {
    mapper().create(build(AcefTerm.AcceptedInfraSpecificTaxa, null));
    mapper().create(build(AcefTerm.AcceptedSpecies, ImmutableMap.of(
        AcefTerm.AcceptedTaxonID, "t1",
        AcefTerm.Genus, "Abies",
        AcefTerm.SpeciesEpithet, "alba",
        DwcTerm.genus, "Abies"
    )));
    mapper().create(build(AcefTerm.AcceptedInfraSpecificTaxa, ImmutableMap.of(
        AcefTerm.AcceptedTaxonID, "t2",
        AcefTerm.ParentSpeciesID, "t1",
        AcefTerm.InfraSpeciesEpithet, "alpina",
        DwcTerm.genus, "Abies"
    ), Issue.BASIONYM_ID_INVALID));
    
    generateDatasetImport(datasetKey);
  }
  
  private static VerbatimRecord build(Term rowType, Map<Term, String> terms, Issue... issues) {
    VerbatimRecord v = new VerbatimRecord();
    v.setLine(line++);
    v.setFile("Test.txt");
    v.setDatasetKey(datasetKey);
    v.setType(rowType);
    if (issues != null) {
      v.getIssues().addAll(Arrays.asList(issues));
    }
    if (terms != null) {
      v.setTerms(terms);
    }
    return v;
  }
  
}