package org.col.dw;

import io.dropwizard.testing.ResourceHelpers;
import org.col.config.WsServerConfig;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class DropwizardPgAppRuleTest {
  
  public static class TestApp extends PgApp<WsServerConfig> {
  
  }
  
  @ClassRule
  public static final DropwizardPgAppRule<WsServerConfig> RULE =
      new DropwizardPgAppRule<>(TestApp.class, ResourceHelpers.resourceFilePath("config-test.yaml"));
  
  @Test
  public void setup() {
    System.out.println("WORKS!");
  }
}