package life.catalogue.resources;

import javax.ws.rs.client.WebTarget;

import io.dropwizard.testing.ResourceHelpers;
import life.catalogue.WsServerRule;
import org.junit.ClassRule;

public class ResourceTestBase {
  
  protected String baseURL;
  protected WebTarget base;
  private final String path;
  
  public ResourceTestBase(String path) {
    this.path = path;
    baseURL = String.format("http://localhost:%d"+path, RULE.getLocalPort());
    base = RULE.client().target(baseURL);
  }
  
  @ClassRule
  public static final WsServerRule RULE = new WsServerRule(ResourceHelpers.resourceFilePath("config-test.yaml"));
  
 
}