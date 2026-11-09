package life.catalogue.matching.decision;

import life.catalogue.api.jackson.ApiModule;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RematchRequestTest {

  /**
   * A request body must not be able to switch off the guard against rematching releases, see gbif/informatics#85 (B6).
   */
  @Test
  public void allowImmutableDatasetsNotBound() throws Exception {
    for (Class<? extends RematchRequest> cl : List.of(RematchRequest.class, SectorRematchRequest.class, DecisionRematchRequest.class)) {
      RematchRequest req = ApiModule.MAPPER.readValue("{\"broken\": true, \"allowImmutableDatasets\": true}", cl);
      assertTrue(req.isBroken());
      assertFalse(cl.getSimpleName(), req.isAllowImmutableDatasets());
    }
  }
}
