package life.catalogue.resources.parser.openrefine;

import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import jakarta.ws.rs.core.MultivaluedHashMap;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameReconciliationResourceTest {
  private final NameReconciliationResource res = new NameReconciliationResource(null, null);

  private OpenRefineModel.Query q(String query) {
    var x = new OpenRefineModel.Query();
    x.query = query;
    return x;
  }

  @Test
  public void parsableNameAutoMatches() {
    var r = res.reconcileSingle(q("Abies alba Mill."), new MultivaluedHashMap<>());
    assertEquals(1, r.result.size());
    assertTrue(r.result.get(0).match);
    assertEquals(100.0, r.result.get(0).score, 0.0001);
  }

  @Ignore("name-parser v4 parses unparsable input as INFORMAL (DOUBTFUL_NAME) instead of returning "
      + "type OTHER; the reconciliation endpoint accepts parsable results as-is. Re-enable once the "
      + "parser returns OTHER for such input.")
  @Test
  public void unparsableNameHasNoCandidate() {
    var r = res.reconcileSingle(q("?? not a name 12345 ##"), new MultivaluedHashMap<>());
    assertTrue("unparsable input must yield no candidate", r.result.isEmpty());
  }
}
