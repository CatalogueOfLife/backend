package org.col.api.jackson;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ApiModuleTest {
  
  @Test
  public void testInit() {
    assertNotNull( ApiModule.MAPPER );
  }
}