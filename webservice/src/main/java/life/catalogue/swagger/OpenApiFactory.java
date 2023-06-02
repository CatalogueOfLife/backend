package life.catalogue.swagger;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.Resources;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.resources.DocsResource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.dropwizard.setup.Environment;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.api.OpenApiContext;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
        .resourcePackages(Set.of(DocsResource.class.getPackageName()))
        .buildContext(false);
      ctxt.setModelConverters(Set.of(resolver));
      ctxt.setOpenApiReader(reader);
      ctxt.init();
      reader.setConfiguration(ctxt.getOpenApiConfiguration());

      OpenAPI oas = ctxt.read();

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

  static Server server(String name, String url){
    var server = new Server();
    server.url(url);
    server.description("COL " + name + " server");
    return server;
  }

}
