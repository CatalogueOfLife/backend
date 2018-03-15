package org.col.api.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.gbif.nameparser.api.NameType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NameSerdeTest extends SerdeTestBase<Name> {

  public NameSerdeTest() {
    super(Name.class);
  }

  @Override
  public Name genTestValue() throws Exception {
    return TestEntityGenerator.newName();
  }

  @Test
  public void testAuthorship() throws JsonProcessingException {
    Name n = TestEntityGenerator.newName();
    String json = ApiModule.MAPPER.writeValueAsString(n);
    assertTrue("Missing authorship property: " + json, json.contains("\"authorship\""));

    n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setType(NameType.SCIENTIFIC);
    json = ApiModule.MAPPER.writeValueAsString(n);
    System.out.println(json);
    assertFalse(json.contains("\"combinationAuthorship\""));
    assertFalse(json.contains("\"basionymAuthorship\""));
    assertFalse(json.contains("\"authorship\""));
  }

}