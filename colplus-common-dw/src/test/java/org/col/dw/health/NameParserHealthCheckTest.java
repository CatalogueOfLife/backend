package org.col.dw.health;

import org.col.dw.health.NameParserHealthCheck;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class NameParserHealthCheckTest {

  @Test
  public void check() throws Exception {
    NameParserHealthCheck ch = new NameParserHealthCheck();
    assertTrue(ch.check().isHealthy());
  }
}