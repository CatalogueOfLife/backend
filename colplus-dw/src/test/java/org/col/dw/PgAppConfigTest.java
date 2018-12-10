package org.col.dw;

import java.io.IOException;
import java.io.InputStream;
import javax.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.col.common.io.Resources;
import org.col.dw.auth.gbif.GBIFAuthenticationFactory;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

public class PgAppConfigTest {
  
  private final ObjectMapper objectMapper = Jackson.newObjectMapper();
  private final Validator validator = Validators.newValidator();
  private final YamlConfigurationFactory<PgAppConfig> factory =
      new YamlConfigurationFactory<>(PgAppConfig.class, validator, objectMapper, "dw");
  
  @Test
  public void testBuildAppCfg() throws Exception {
    final PgAppConfig cfg = factory.build(new ConfigurationSourceProvider() {
      @Override
      public InputStream open(String path) throws IOException {
        return Resources.stream(path);
      }
    }, "config-test.yaml");
    assertNotNull(cfg.auth);
    assertThat(cfg.auth).isInstanceOf(GBIFAuthenticationFactory.class);
  }

}
