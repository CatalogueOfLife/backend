package life.catalogue.swagger;

import life.catalogue.WsServerConfig;

import java.util.Map;

import org.junit.Test;

import io.dropwizard.core.setup.Environment;
import io.swagger.v3.oas.models.media.Schema;

import static org.junit.Assert.*;

public class OpenApiFactoryTest {

  @Test
  public void build() {
    WsServerConfig cfg = new WsServerConfig();
    Environment env = new Environment("test");
    var api = OpenApiFactory.build(cfg, env);
    api.getServers().forEach(System.out::println);
    api.getComponents().getSecuritySchemes().forEach((k,v) -> System.out.println(k+": "+v));
    api.getSecurity().forEach(System.out::println);

    // labelHtml documents that it is safe for innerHTML, see backend#1699
    Map<String, Schema> schemas = api.getComponents().getSchemas();
    assertEquals(OpenApiFactory.LABEL_HTML_DESCRIPTION, labelHtml(schemas.get("SimpleName")).getDescription());
    long described = schemas.values().stream()
      .filter(s -> labelHtml(s) != null)
      .peek(s -> assertEquals(s.getName(), OpenApiFactory.LABEL_HTML_DESCRIPTION, labelHtml(s).getDescription()))
      .count();
    assertTrue(described > 1);
  }

  private static Schema<?> labelHtml(Schema<?> schema) {
    assertNotNull(schema);
    return schema.getProperties() == null ? null : schema.getProperties().get("labelHtml");
  }
}