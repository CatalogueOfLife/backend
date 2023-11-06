package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.vocab.*;

import org.apache.ibatis.session.SqlSession;

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DatasetImportDaoTest extends DaoTestBase {
  
  DatasetImportDao dao;

  @Before
  public void init() {
    dao = new DatasetImportDao(factory(), null);
  }

  @Test
  public void generateDatasetImport() throws SQLException {
    DatasetImport d = dao.generateMetrics(TestEntityGenerator.DATASET11.getKey(), Users.TESTER);
  
    assertEquals((Integer) 0, d.getTreatmentCount());
    assertEquals((Integer) 0, d.getMediaCount());
    assertEquals((Integer) 3, d.getReferenceCount());
    assertEquals((Integer) 2, d.getTaxonCount());
    assertEquals((Integer) 2, d.getSynonymCount());
    assertEquals((Integer) 5, d.getDistributionCount());
    assertEquals((Integer) 3, d.getVernacularCount());
    assertEquals((Integer) 5, d.getNameCount());
    assertEquals((Integer) 5, d.getVerbatimCount());

    assertEquals( 6, d.getIssuesCount().keySet().size());
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.ESCAPED_CHARACTERS));
    assertEquals((Integer) 2, d.getIssuesCount().get(Issue.REFERENCE_ID_INVALID));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.ID_NOT_UNIQUE));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.URL_INVALID));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.INCONSISTENT_AUTHORSHIP));
    assertEquals((Integer) 1, d.getIssuesCount().get(Issue.UNUSUAL_NAME_CHARACTERS));
    assertFalse(d.getIssuesCount().containsKey(Issue.NOT_INTERPRETED));
    assertFalse(d.getIssuesCount().containsKey(Issue.NULL_EPITHET));
  
    assertEquals( 0, d.getMediaByTypeCount().size());

    assertEquals( 1, d.getNamesByRankCount().size());
    assertEquals((Integer) 5, d.getNamesByRankCount().get(Rank.SPECIES));
    
    assertEquals(1, d.getUsagesByOriginCount().size());
    assertEquals((Integer) 4, d.getUsagesByOriginCount().get(Origin.SOURCE));
    
    assertEquals(1, d.getNamesByTypeCount().size());
    assertEquals((Integer) 5, d.getNamesByTypeCount().get(NameType.SCIENTIFIC));
    
    assertEquals(3, d.getDistributionsByGazetteerCount().size());
    assertEquals((Integer) 3, d.getDistributionsByGazetteerCount().get(Gazetteer.TEXT));
    
    assertEquals(3, d.getVernacularsByLanguageCount().size());
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get("deu"));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get("eng"));
    assertEquals((Integer) 1, d.getVernacularsByLanguageCount().get("nld"));
    
    assertEquals(2, d.getUsagesByStatusCount().size());
    assertEquals((Integer) 2, d.getUsagesByStatusCount().get(TaxonomicStatus.ACCEPTED));
    assertEquals((Integer) 2, d.getUsagesByStatusCount().get(TaxonomicStatus.SYNONYM));
    
    assertEquals(0, d.getNamesByStatusCount().size());
    
    assertEquals(1, d.getNameRelationsByTypeCount().size());
    assertEquals((Integer) 1, d.getNameRelationsByTypeCount().get(NomRelType.SPELLING_CORRECTION));

    assertEquals( 0, d.getSpeciesInteractionsByTypeCount().size());
    assertEquals( 0, d.getTaxonConceptRelationsByTypeCount().size());

    assertEquals(2, d.getVerbatimByTermCount().size());
    assertEquals((Integer) 3, d.getVerbatimByTermCount().get(AcefTerm.AcceptedSpecies));
    assertEquals(2, d.getVerbatimByRowTypeCount().size());
    assertEquals(18, d.getVerbatimByRowTypeCount().get(AcefTerm.AcceptedSpecies).size());
    for (Term t : d.getVerbatimByRowTypeCount().get(AcefTerm.AcceptedSpecies).keySet()) {
      assertEquals(AcefTerm.class, t.getClass());
    }
  }

}
