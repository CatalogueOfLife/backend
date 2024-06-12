package life.catalogue.swagger;

import life.catalogue.WsServerConfig;

import org.junit.Test;

import io.dropwizard.core.setup.Environment;

public class OpenApiFactoryTest {

  @Test
  public void build() {
    WsServerConfig cfg = new WsServerConfig();
    Environment env = new Environment("test");
    var api = OpenApiFactory.build(cfg, env);
    api.getServers().forEach(System.out::println);
    api.getComponents().getSecuritySchemes().forEach((k,v) -> System.out.println(k+": "+v));
    api.getSecurity().forEach(System.out::println);
  }
}