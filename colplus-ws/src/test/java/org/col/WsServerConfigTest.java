package org.col;

import java.io.IOException;
import java.io.InputStream;
import javax.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.assertj.core.api.Assertions;
import org.col.common.io.Resources;
import org.col.dw.auth.map.MapAuthenticationFactory;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class WsServerConfigTest {
  
  private static final ObjectMapper objectMapper = Jackson.newObjectMapper();
  private static final Validator validator = Validators.newValidator();
  private static final YamlConfigurationFactory<WsServerConfig> factory =
      new YamlConfigurationFactory<>(WsServerConfig.class, validator, objectMapper, "dw");
  
  public static WsServerConfig readTestConfig() throws Exception {
    return factory.build(new ConfigurationSourceProvider() {
      @Override
      public InputStream open(String path) throws IOException {
        return Resources.stream(path);
      }
    }, "config-test.yaml");
  }

  @Test
  public void testBuildAppCfg() throws Exception {
    final WsServerConfig cfg = readTestConfig();
    assertNotNull(cfg.auth);
    Assertions.assertThat(cfg.auth).isInstanceOf(MapAuthenticationFactory.class);
    
    String version = cfg.versionString();
    System.out.println(version);
    assertNotNull(version);
    assertFalse(version.contains("{"));
  }
}