package life.catalogue.api.search;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeMapEnumKeyTestBase;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameUsageSearchParameterSerdeTest extends SerdeMapEnumKeyTestBase<NameUsageSearchParameter> {

  public NameUsageSearchParameterSerdeTest() {
    super(NameUsageSearchParameter.class);
  }

  @Test
  public void noWhitespace() throws JsonProcessingException {
    for (NameUsageSearchParameter p : NameUsageSearchParameter.values()) {
      String json = ApiModule.MAPPER.writeValueAsString(p);
      System.out.println(p +" -> " + json);
      assertFalse(json.contains(" "));
      assertTrue(Character.isLowerCase(json.charAt(1)));
    }
  }
}