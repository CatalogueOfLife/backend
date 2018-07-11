package org.col.dw;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.ClassRule;
import org.junit.Test;

public class DropwizardPgAppRuleTest {

  public static class TestApp extends PgApp<PgAppConfig> {

  }

  @ClassRule
  public static final DropwizardPgAppRule<PgAppConfig> RULE =
      new DropwizardPgAppRule<>(TestApp.class, ResourceHelpers.resourceFilePath("config-test.yaml"));

  @Test
  public void setup() {
    System.out.println("WORKS!");
  }
}