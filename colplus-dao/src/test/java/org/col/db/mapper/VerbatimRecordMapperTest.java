package org.col.db.mapper;

import java.util.Arrays;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Page;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Issue;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.DATASET11;
import static org.col.api.TestEntityGenerator.TAXON1;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class VerbatimRecordMapperTest extends MapperTestBase<VerbatimRecordMapper> {
  private static long line = 1;
  private static final int datasetKey = DATASET11.getKey();
  
  public VerbatimRecordMapperTest() {
    super(VerbatimRecordMapper.class);
  }
  
  @Test
  public void roundtrip() {
    VerbatimRecord r1 = TestEntityGenerator.createVerbatim();
    mapper().create(r1);
    
    commit();
    
    VerbatimRecord r2 = mapper().get(r1.getDatasetKey(), r1.getKey());
    
    assertEquals(r1, r2);
  }
  
  @Test
  public void list() {
    assertEquals(0, mapper().list(TAXON1.getDatasetKey(), null, null,
        Lists.newArrayList(Issue.ACCEPTED_ID_INVALID),
        new Page()).size());
    assertEquals(1, mapper().list(TAXON1.getDatasetKey(), null, null,
        Lists.newArrayList(Issue.ID_NOT_UNIQUE),
        new Page()).size());
  
    insertTestData();
    
  }
  
  @Test
  public void count() {
    // count apples. rely on import metrics for quick counts so derive them first
    generateDatasetImport(DATASET11.getKey());
    assertEquals(5, mapper().count(datasetKey, null, null, null));
    assertEquals(3, mapper().count(datasetKey, AcefTerm.AcceptedSpecies, null, null));
    assertEquals(0, mapper().count(datasetKey, AcefTerm.AcceptedInfraSpecificTaxa, null, null));
  
    insertTestData();
    assertEquals(8, mapper().count(datasetKey, null, null, null));
    assertEquals(2, mapper().count(datasetKey, AcefTerm.AcceptedInfraSpecificTaxa, null, null));
    assertEquals(1, mapper().count(datasetKey, AcefTerm.AcceptedInfraSpecificTaxa, ImmutableMap.of(DwcTerm.genus, "Abies"), null));
    assertEquals(2, mapper().count(datasetKey, null, ImmutableMap.of(DwcTerm.genus, "Abies"), null));
    assertEquals(1, mapper().count(datasetKey, null, ImmutableMap.of(DwcTerm.genus, "Abies"), Lists.newArrayList(Issue.BASIONYM_ID_INVALID)));
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