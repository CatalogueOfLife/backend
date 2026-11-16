package life.catalogue.matching;

import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.TabularFormat;

import java.io.File;
import java.util.Map;

import jakarta.ws.rs.QueryParam;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MatchingRequestTest {

  @Test
  public void resultFileName() {
    var req = new MatchingRequest();
    assertEquals("match-dataset-null.csv", req.resultFileName());

    req.setSourceDatasetKey(1234);
    assertEquals("match-dataset-1234.csv", req.resultFileName());

    req = new MatchingRequest();
    req.setUpload(new File("/tmp/3456dfgh.csv"));
    assertEquals("match-3456dfgh.csv", req.resultFileName());
    req.setFormat(TabularFormat.TSV);
    assertEquals("match-3456dfgh.tsv", req.resultFileName());
  }

  /**
   * The match job endpoints bind MatchingRequest as a @BeanParam, so the query parameters of the traversal it inherits
   * come from the annotations on TreeTraversalParameter. The ChecklistBank UI sends taxonID.
   */
  @Test
  public void inheritedQueryParams() throws Exception {
    assertEquals(TreeTraversalParameter.class, MatchingRequest.class.getSuperclass());
    var expected = Map.of("taxonID", "taxonID", "exclusion", "exclusion", "lowestRank", "lowestRank", "synonyms", "synonyms");
    for (var e : expected.entrySet()) {
      var qp = TreeTraversalParameter.class.getDeclaredField(e.getKey()).getAnnotation(QueryParam.class);
      assertNotNull(e.getKey() + " is not bound", qp);
      assertEquals(e.getValue(), qp.value());
    }
    // no meaning for matching
    assertNull(TreeTraversalParameter.class.getDeclaredField("sectorless").getAnnotation(QueryParam.class));
  }
}
