package org.col.db.dao;

import org.col.api.TestEntityGenerator;
import org.col.api.model.DatasetImport;
import org.col.api.vocab.*;
import org.col.db.mapper.DatasetImportMapper;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DatasetImportDaoTest extends DaoTestBase {
  
  @Test
  public void generateDatasetImport() {
    
    DatasetImportDao dao = new DatasetImportDao(factory());
    
    DatasetImport d = new DatasetImport();
    d.setDatasetKey(TestEntityGenerator.DATASET11.getKey());
    dao.updateMetrics(mapper(DatasetImportMapper.class), d);
    
    assertEquals((Integer) 5, d.getNameCount());
    assertEquals((Integer) 2, d.getTaxonCount());
    assertEquals((Integer) 2, d.getReferenceCount());
    assertEquals((Integer) 5, d.getVerbatimCount());
    assertEquals((Integer) 3, d.getVernacularCount());
    assertEquals((Integer) 3, d.getDistributionCount());
    
    assertEquals(6, d.getIssuesCount().keySet().size());
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.ESCAPED_CHARACTERS));
    assertEquals((Integer) 2, d.getIssuesCount().get(Issue.REFERENCE_ID_INVALID));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.ID_NOT_UNIQUE));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.URL_INVALID));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.INCONSISTENT_AUTHORSHIP));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.UNUSUAL_NAME_CHARACTERS));
    assertFalse(d.getIssuesCount().containsKey(Issue.NOT_INTERPRETED));
    assertFalse(d.getIssuesCount().containsKey(Issue.NULL_EPITHET));
    
    assertEquals(1, d.getNamesByRankCount().size());
    assertEquals((Integer) 5, d.getNamesByRankCount().get(Rank.SPECIES));
    
    assertEquals(1, d.getNamesByOriginCount().size());
    assertEquals((Integer) 5, d.getNamesByOriginCount().get(Origin.SOURCE));
    
    assertEquals(1, d.getNamesByTypeCount().size());
    assertEquals((Integer) 5, d.getNamesByTypeCount().get(NameType.SCIENTIFIC));
    
    assertEquals(1, d.getDistributionsByGazetteerCount().size());
    assertEquals((Integer) 3, d.getDistributionsByGazetteerCount().get(Gazetteer.TEXT));
    
    assertEquals(3, d.getVernacularsByLanguageCount().size());
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.GERMAN));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.ENGLISH));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get(Language.DUTCH));
    
    assertEquals(2, d.getUsagesByStatusCount().size());
    assertEquals((Integer) 2, d.getUsagesByStatusCount().get(TaxonomicStatus.ACCEPTED));
    assertEquals((Integer) 2, d.getUsagesByStatusCount().get(TaxonomicStatus.SYNONYM));
    
    assertEquals(0, d.getNamesByStatusCount().size());
    
    assertEquals(1, d.getNameRelationsByTypeCount().size());
    assertEquals((Integer) 1, d.getNameRelationsByTypeCount().get(NomRelType.SPELLING_CORRECTION));
    
    assertEquals(2, d.getVerbatimByTypeCount().size());
    assertEquals((Integer) 3, d.getVerbatimByTypeCount().get(AcefTerm.AcceptedSpecies));
  }
}
