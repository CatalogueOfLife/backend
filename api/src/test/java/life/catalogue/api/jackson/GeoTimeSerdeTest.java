package life.catalogue.api.jackson;

import life.catalogue.api.vocab.GeoTime;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class GeoTimeSerdeTest {
  
  @Test
  public void serializeParent() throws Exception {
    GeoTime gt = GeoTime.byName("Aalenian");
    assertNotNull(gt.getParent());

    String json = ApiModule.MAPPER.writeValueAsString(new SerdeTestBase.Wrapper<>(gt));
    System.out.println(json);
    assertFalse(StringUtils.isBlank(json));
    assertTrue(json.contains("\"parent\":\"MiddleJurassic\""));
  }
  
}
