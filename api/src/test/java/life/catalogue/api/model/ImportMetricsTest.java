package life.catalogue.api.model;

import life.catalogue.api.vocab.Gazetteer;
import life.catalogue.api.vocab.Issue;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class ImportMetricsTest {

    @Test
    public void add() {
      ImportMetrics m = new ImportMetrics();
      m.setNameCount(1000);
      m.setIssuesCount(Map.of(Issue.ACCEPTED_NAME_MISSING, 78, Issue.BASIONYM_ID_INVALID, 78));

      ImportMetrics m2 = new ImportMetrics();
      m2.setNameCount(1000);
      m2.setVernacularCount(456);
      m2.setIssuesCount(Map.of(Issue.ACCEPTED_NAME_MISSING, 2, Issue.ACCEPTED_ID_INVALID, 3));
      m2.setDistributionsByGazetteerCount(Map.of(Gazetteer.ISO, 897));

      m.add(m2);

      assertEquals(2000, (int) m.getNameCount());
      assertEquals(456, (int) m.getVernacularCount());
      assertNull(m.getTaxonCount());
      assertTrue(m.getTaxonConceptRelationsByTypeCount().isEmpty());
      assertTrue(m.getSpeciesInteractionsByTypeCount().isEmpty());
      assertEquals(3, m.getIssuesCount().size());
      assertEquals(80, (int) m.getIssuesCount().get(Issue.ACCEPTED_NAME_MISSING));
      assertEquals(78, (int) m.getIssuesCount().get(Issue.BASIONYM_ID_INVALID));
      assertEquals(3, (int) m.getIssuesCount().get(Issue.ACCEPTED_ID_INVALID));
      assertEquals(897, (int) m.getDistributionsByGazetteerCount().get(Gazetteer.ISO));
    }
}