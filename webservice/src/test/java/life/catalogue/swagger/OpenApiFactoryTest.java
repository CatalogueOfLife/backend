package life.catalogue.swagger;

import io.dropwizard.setup.Environment;

import life.catalogue.WsServerConfig;

import org.junit.Test;
import org.neo4j.cypher.internal.v3_4.functions.E;

import static org.junit.Assert.*;

public class OpenApiFactoryTest {

  @Test
  public void build() {
    WsServerConfig cfg = new WsServerConfig();
    Environment env = new Environment("test");
    var api = OpenApiFactory.build(cfg, env);
    api.getServers().forEach(System.out::println);
  }
}