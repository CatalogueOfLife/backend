package org.col;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.ClassRule;
import org.junit.Test;

public class WsServerRuleTest {
  
  @ClassRule
  public static final WsServerRule RULE = new WsServerRule(ResourceHelpers.resourceFilePath("config-test.yaml"));
  
  @Test
  public void setup() {
    System.out.println("WORKS!");
  }
}