package org.col.api.model;

import org.col.api.TestEntityGenerator;
import org.col.api.jackson.ApiModule;
import org.col.api.jackson.SerdeTestBase;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ReferenceTest extends SerdeTestBase<Reference> {
  
  public ReferenceTest() {
    super(Reference.class);
  }
  
  @Override
  public Reference genTestValue() throws Exception {
    return TestEntityGenerator.newReference();
  }
  
  @Test
  public void testAbstract() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(genTestValue());
    assertTrue(json.contains("\"abstract\""));
    assertFalse(json.contains("\"abstrct\""));
  }
  
  @Override
  protected void debug(String json, Wrapper<Reference> wrapper, Wrapper<Reference> wrapper2) {
    System.out.println(json);
  }
}