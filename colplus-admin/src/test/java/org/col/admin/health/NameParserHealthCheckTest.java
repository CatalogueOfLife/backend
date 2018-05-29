package org.col.admin.health;

import com.codahale.metrics.health.HealthCheck;
import org.junit.Test;

import static org.junit.Assert.*;

public class NameParserHealthCheckTest {

  @Test
  public void check() throws Exception {
    NameParserHealthCheck ch = new NameParserHealthCheck();
    assertTrue(ch.check().isHealthy());
  }
}