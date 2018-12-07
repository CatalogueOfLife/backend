package org.col.dw.auth;

import java.io.IOException;
import java.io.InputStream;
import javax.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.DiscoverableSubtypeResolver;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import org.col.common.io.Resources;
import org.col.dw.auth.gbif.GBIFAuthenticationFactory;
import org.col.dw.auth.map.MapAuthenticationFactory;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AuthenticationProviderTest {
  
  private final ObjectMapper objectMapper = Jackson.newObjectMapper();
  private final Validator validator = Validators.newValidator();
  private final YamlConfigurationFactory<AuthenticationProviderFactory> factory =
      new YamlConfigurationFactory<>(AuthenticationProviderFactory.class, validator, objectMapper, "dw");
  
  @Test
  public void isDiscoverable() throws Exception {
    // Make sure the types we specified in META-INF gets picked up
    assertThat(new DiscoverableSubtypeResolver().getDiscoveredSubtypes())
        .contains(GBIFAuthenticationFactory.class)
        .contains(MapAuthenticationFactory.class);
  }
  
  @Test
  public void testBuildGbif() throws Exception {
    final AuthenticationProviderFactory apf = factory.build(new ConfigurationSourceProvider() {
      @Override
      public InputStream open(String path) throws IOException {
        return Resources.stream(path);
      }
    }, "gbifAuth.yaml");
    assertThat(apf).isInstanceOf(GBIFAuthenticationFactory.class);
  }
  
  @Test
  public void testBuildMap() throws Exception {
    final AuthenticationProviderFactory apf = factory.build(new ConfigurationSourceProvider() {
      @Override
      public InputStream open(String path) throws IOException {
        return Resources.stream(path);
      }
    }, "mapAuth.yaml");
    assertThat(apf).isInstanceOf(MapAuthenticationFactory.class);
    MapAuthenticationFactory.MapAuthentication maf = (MapAuthenticationFactory.MapAuthentication) apf.createAuthenticationProvider();
    assertEquals(3, maf.getUsers().size());
    for (MapAuthenticationFactory.Cred c : maf.getUsers().values()) {
      assertNotNull(c.username);
      assertNotNull(c.password);
    }
  }

}
