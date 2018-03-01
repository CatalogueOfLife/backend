package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NameSerdeTest extends SerdeTestBase<Name> {

  public NameSerdeTest() {
    super(Name.class);
  }

  @Override
  Name genTestValue() throws Exception {
    return TestEntityGenerator.newName();
  }

  @Test
  public void testAuthorship() throws JsonProcessingException {
    String json = ApiModule.MAPPER.writeValueAsString(TestEntityGenerator.newName());
    assertTrue("Missing authorship property: " + json, json.contains("\"authorship\""));
  }

}