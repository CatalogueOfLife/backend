package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.jackson.ApiModule;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParserOpenRefineModelTest {

  @Test
  public void extendServiceCarriesPropertySettings() throws Exception {
    var svc = new OpenRefineModel.ExtendService(
      new OpenRefineModel.PropertySettings("http://x/parser/name/reconcile", "/extend/propose"));
    var code = new OpenRefineModel.PropertySetting();
    code.name = "code";
    code.label = "Nomenclatural code";
    code.type = "select";
    code.default_ = "ICZN";
    code.choices = List.of(new OpenRefineModel.SettingChoice("ICZN", "Zoological (ICZN)"));
    svc.property_settings = List.of(code);

    String json = ApiModule.MAPPER.writeValueAsString(svc);
    assertTrue(json.contains("\"property_settings\""));
    assertTrue(json.contains("\"ICZN\""));
    assertTrue(json.contains("\"default\""));
  }

  @Test
  public void extendPropertyParsesSettings() throws Exception {
    String body = "{\"id\":\"genus\",\"settings\":{\"code\":\"ICZN\",\"rank\":\"species\"}}";
    var p = ApiModule.MAPPER.readValue(body, OpenRefineModel.ExtendProperty.class);
    assertEquals("genus", p.id);
    assertNotNull(p.settings);
    assertEquals("ICZN", p.settings.get("code").asText());
    assertEquals("species", p.settings.get("rank").asText());
  }
}
