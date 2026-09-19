package life.catalogue.release.review;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ManagedAgentsClientTest {
  private static final ObjectMapper OM = new ObjectMapper();

  private static JsonNode session(String json) throws Exception {
    return OM.readTree(json);
  }

  /**
   * The API reports list_cost in minor units, just like the max_list_cost of the budget we send.
   * A 6.05 dollar session used to be rendered as "605 USD".
   */
  @Test
  public void costInMinorUnits() throws Exception {
    assertEquals("6.05 USD", ManagedAgentsClient.cost(session("{\"usage\":{\"list_cost\":{\"amount\":\"605\",\"currency\":\"USD\"}}}")));
    assertEquals("25.00 USD", ManagedAgentsClient.cost(session("{\"usage\":{\"list_cost\":{\"amount\":\"2500\",\"currency\":\"USD\"}}}")));
    assertEquals("0.07 USD", ManagedAgentsClient.cost(session("{\"usage\":{\"list_cost\":{\"amount\":\"7\",\"currency\":\"USD\"}}}")));
  }

  @Test
  public void costUnknownShape() throws Exception {
    assertNull(ManagedAgentsClient.cost(session("{\"status\":\"idle\"}")));
    // not an integer amount of minor units - reported verbatim rather than guessed at
    assertEquals("6.05 USD", ManagedAgentsClient.cost(session("{\"usage\":{\"list_cost\":{\"amount\":\"6.05\",\"currency\":\"USD\"}}}")));
    assertEquals("605", ManagedAgentsClient.cost(session("{\"usage\":{\"list_cost\":{\"amount\":\"605\"}}}")));
  }
}
