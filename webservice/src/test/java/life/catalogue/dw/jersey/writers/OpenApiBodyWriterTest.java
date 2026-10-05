package life.catalogue.dw.jersey.writers;

import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.resources.OpenApiResource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import jakarta.ws.rs.core.MediaType;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(DropwizardExtensionsSupport.class)
public class OpenApiBodyWriterTest {

  static final OpenAPI API = new OpenAPI()
    .info(new Info().title("Test API").version("1.0"))
    .components(new Components().addSchemas("key", new IntegerSchema()))
    .addSecurityItem(new SecurityRequirement().addList("basicAuth").addList("jwt"));

  static final ResourceExtension EXT = ResourceExtension.builder()
    .addResource(new OpenApiResource(API))
    .addProvider(OpenApiBodyWriter.class)
    .build();

  String get(String mediaType) {
    var resp = EXT.target("/openapi").request(mediaType).get();
    assertEquals(200, resp.getStatus());
    return resp.readEntity(String.class);
  }

  @Test
  public void yaml() {
    for (String mt : new String[]{MoreMediaTypes.TEXT_YAML, MoreMediaTypes.APP_YAML, MoreMediaTypes.APP_X_YAML}) {
      String yaml = get(mt);
      assertTrue(yaml.startsWith("openapi: 3.0.1"), yaml);
      assertTrue(yaml.contains("title: Test API"), yaml);
      assertFalse(yaml.contains("exampleSetFlag"), yaml);
    }
  }

  @Test
  public void json() {
    String json = get(MediaType.APPLICATION_JSON);
    assertTrue(json.startsWith("{"), json);
    assertTrue(json.contains("\"title\":\"Test API\""), json);
    assertTrue(json.contains("\"security\":[{\"basicAuth\":[],\"jwt\":[]}]"), json);
    assertFalse(json.contains("exampleSetFlag"), json);
  }

}
