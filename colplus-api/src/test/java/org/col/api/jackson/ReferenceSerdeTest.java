package org.col.api.jackson;

import org.col.api.TestEntityGenerator;
import org.col.api.model.Reference;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ReferenceSerdeTest extends SerdeTestBase<Reference> {

  public ReferenceSerdeTest() {
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
  protected void debug(String json, Wrapper<Reference> wrapper, Wrapper<Reference> wrapper2){
    System.out.println(json);
  }
}