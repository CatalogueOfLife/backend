 package life.catalogue;

 import life.catalogue.common.io.Resources;
 import life.catalogue.dw.auth.map.MapAuthenticationFactory;

 import java.io.File;
 import java.io.IOException;
 import java.io.InputStream;
 import java.util.UUID;

 import jakarta.validation.Validator;

 import org.assertj.core.api.Assertions;
 import org.junit.Test;

 import com.fasterxml.jackson.databind.ObjectMapper;

 import io.dropwizard.configuration.ConfigurationSourceProvider;
 import io.dropwizard.configuration.YamlConfigurationFactory;
 import io.dropwizard.jackson.Jackson;
 import io.dropwizard.jersey.validation.Validators;

 import static org.junit.Assert.assertEquals;
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
  }

  @Test
  public void downloadFile() {
    WsServerConfig cfg = new WsServerConfig();
    cfg.job.downloadDir = new File("/tmp/col");
    UUID key = UUID.fromString("7ca06f44-2c0c-4fa9-a410-ac072c378378");
    assertEquals(new File("/tmp/col/7c/7ca06f44-2c0c-4fa9-a410-ac072c378378.zip"), cfg.job.downloadFile(key));
  }

}