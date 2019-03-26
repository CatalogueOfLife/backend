package org.col.resources;

import javax.ws.rs.client.WebTarget;

import io.dropwizard.testing.ResourceHelpers;
import org.col.WsServer;
import org.col.config.WsServerConfig;
import org.col.dw.DropwizardPgAppRule;
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
  public static final DropwizardPgAppRule<WsServerConfig> RULE =
      new DropwizardPgAppRule<>(WsServer.class, ResourceHelpers.resourceFilePath("config-test.yaml"));
  
 
}