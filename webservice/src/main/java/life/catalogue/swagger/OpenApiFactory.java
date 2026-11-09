package life.catalogue.swagger;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.Resources;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.resources.VersionResource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.dropwizard.core.setup.Environment;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.api.OpenApiContext;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

public class OpenApiFactory {
  private OpenApiFactory(){};

  public static OpenAPI build(WsServerConfig cfg, Environment env) {

    ModelResolver resolver = new ModelResolver(env.getObjectMapper());
    ModelConverters.getInstance().addConverter(resolver);

    try {
      // removes @Auth params from request body
      DWReader reader = new DWReader();
      OpenApiContext ctxt = new JaxrsOpenApiContextBuilder()
        .resourcePackages(Set.of(VersionResource.class.getPackageName()))
        .buildContext(false);
      ctxt.setModelConverters(Set.of(resolver));
      ctxt.setOpenApiReader(reader);
      ctxt.init();
      reader.setConfiguration(ctxt.getOpenApiConfiguration());

      OpenAPI oas = ctxt.read();
      describeLabels(oas);

      // we set the version programmatically from our git properties if they exist
      Info info  = YamlUtils.read(Info.class, Resources.stream("openapi-info.yaml"));
      String v = cfg.versionString();
      if (v != null) {
        info.setVersion(v);
      }
      oas.setInfo(info);

      // add fixed prod & dev servers
      oas.setServers(List.of(
        server("production", "https://api.checklistbank.org"),
        server("test", "https://api.dev.checklistbank.org")
      ));

      // security
      oas.getComponents()
        .addSecuritySchemes("basicAuth",
          new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
        )
        .addSecuritySchemes("jwt",
        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
      );
      oas.addSecurityItem(
        new SecurityRequirement()
          .addList("basicAuth")
          .addList("jwt")
      );
      return oas;

    } catch (OpenApiConfigurationException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  static final String LABEL_HTML_DESCRIPTION = "The label as HTML with the epithets in italics. "
    + "Every part is HTML escaped, so it is safe to use as element content (innerHTML) and contains no markup other than <i> and </i>. "
    + "It is not escaped for use inside an attribute value.";

  /**
   * The schemas are generated from the jackson model, which carries no descriptions.
   * Document the guarantee labelHtml makes wherever it appears, see backend#1699
   */
  static void describeLabels(OpenAPI oas) {
    if (oas.getComponents() == null || oas.getComponents().getSchemas() == null) return;
    for (Schema<?> schema : oas.getComponents().getSchemas().values()) {
      if (schema.getProperties() != null && schema.getProperties().containsKey("labelHtml")) {
        schema.getProperties().get("labelHtml").setDescription(LABEL_HTML_DESCRIPTION);
      }
    }
  }

  static Server server(String name, String url){
    var server = new Server();
    server.url(url);
    server.description("COL " + name + " server");
    return server;
  }

}
