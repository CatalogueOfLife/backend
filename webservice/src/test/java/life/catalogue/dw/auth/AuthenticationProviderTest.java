package life.catalogue.dw.auth;

import life.catalogue.common.io.Resources;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.dw.auth.gbif.GBIFAuthenticationFactory;
import life.catalogue.dw.auth.map.MapAuthenticationFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.validation.Validator;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;

import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.DiscoverableSubtypeResolver;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;

public class AuthenticationProviderTest {
  
  private final ObjectMapper objectMapper = Jackson.newObjectMapper();
  private final Validator validator = Validators.newValidator();
  private final YamlConfigurationFactory<AuthenticationProviderFactory> factory =
      new YamlConfigurationFactory<>(AuthenticationProviderFactory.class, validator, objectMapper, "dw");
  
  @Test
  public void isDiscoverable() throws Exception {
    // Make sure the types we specified in META-INF gets picked up
    Assertions.assertThat(new DiscoverableSubtypeResolver().getDiscoveredSubtypes())
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
    Assertions.assertThat(apf).isInstanceOf(GBIFAuthenticationFactory.class);
  }
  
  @Test
  public void testBuildMap() throws Exception {
    final AuthenticationProviderFactory apf = factory.build(new ConfigurationSourceProvider() {
      @Override
      public InputStream open(String path) throws IOException {
        return Resources.stream(path);
      }
    }, "mapAuth.yaml");
    Assertions.assertThat(apf).isInstanceOf(MapAuthenticationFactory.class);
    MapAuthenticationFactory.MapAuthentication maf = (MapAuthenticationFactory.MapAuthentication) apf.createAuthenticationProvider();
    Assert.assertEquals(3, maf.getUsers().size());
    for (MapAuthenticationFactory.Cred c : maf.getUsers().values()) {
      Assert.assertNotNull(c.username);
      Assert.assertNotNull(c.password);
    }
  }
  
  public static class TestConfig {
    public AuthenticationProviderFactory apf;
  }
  
  @Test
  public void testWriteMap() throws Exception {
    final TestConfig cfg = new TestConfig();
    MapAuthenticationFactory maf = new MapAuthenticationFactory();
    MapAuthenticationFactory.Cred c = new MapAuthenticationFactory.Cred();
    c.username = "bfdvs";
    maf.users.add(c);
    cfg.apf = maf;
    
    File f = File.createTempFile("col6432", "fd");
    System.out.println(f.getAbsolutePath());
    YamlUtils.write(cfg, f);
    
    System.out.println(FileUtils.readFileToString(f, Charsets.UTF_8));
  }

}
