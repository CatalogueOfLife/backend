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

import static org.junit.Assert.*;

public class WsServerConfigTest {
  
  private final ObjectMapper objectMapper = Jackson.newObjectMapper();
  private final Validator validator = Validators.newValidator();
  private final YamlConfigurationFactory<WsServerConfig> factory =
      new YamlConfigurationFactory<>(WsServerConfig.class, validator, objectMapper, "dw");
  
  @Test
  public void testBuildAppCfg() throws Exception {
    final WsServerConfig cfg = factory.build(new ConfigurationSourceProvider() {
      @Override
      public InputStream open(String path) throws IOException {
        return Resources.stream(path);
      }
    }, "config-test.yaml");
    assertNotNull(cfg.auth);
    Assertions.assertThat(cfg.auth).isInstanceOf(MapAuthenticationFactory.class);
  }
}