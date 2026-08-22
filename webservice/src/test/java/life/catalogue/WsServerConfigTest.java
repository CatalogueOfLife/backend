 package life.catalogue;

 import life.catalogue.common.io.Resources;
 import life.catalogue.dw.auth.map.MapAuthenticationFactory;

 import java.io.File;
 import java.io.FileInputStream;
 import java.io.IOException;
 import java.io.InputStream;
 import java.util.UUID;

 import org.assertj.core.api.Assertions;
 import org.junit.Test;

 import com.fasterxml.jackson.databind.JsonNode;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

 import io.dropwizard.configuration.ConfigurationSourceProvider;
 import io.dropwizard.configuration.YamlConfigurationFactory;
 import io.dropwizard.jackson.Jackson;
 import io.dropwizard.jersey.validation.Validators;
 import jakarta.validation.Validator;

 import static org.junit.Assert.assertEquals;
 import static org.junit.Assert.assertNotNull;
 import static org.junit.Assert.assertNull;
 import static org.junit.Assert.assertTrue;

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

  private static File envConfig(String name) {
    File f = new File(name);
    if (!f.exists()) {
      f = new File("webservice", name); // when run from the reactor root
    }
    assertTrue("missing " + name, f.exists());
    return f;
  }

  /**
   * The shipped environment configs must stay in sync with the config classes.
   * The yaml factory silently ignores unknown properties, so a setting that moved in java but was left behind
   * in one of these files would be dropped without a word - which is how an import lane silently falls back to
   * a single thread. Assert on the parsed yaml tree instead of trusting the binding.
   */
  @Test
  public void environmentConfigsMatchConfigClasses() throws Exception {
    final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    for (String name : new String[]{"config-prod.yaml", "config-dev.yaml", "config-local.yaml"}) {
      File f = envConfig(name);
      // it must bind cleanly...
      WsServerConfig cfg = factory.build(path -> new FileInputStream(f), name);
      assertNotNull(name, cfg.job);
      assertTrue(name + " must size the import lane", cfg.job.importThreads >= 1);
      assertTrue(name + " must size the import queue", cfg.job.importQueue >= 1);

      // ...and it must not still carry the settings that moved onto cfg.job, which would bind to nothing
      JsonNode root = yaml.readTree(f);
      JsonNode importer = root.get("importer");
      if (importer != null) {
        for (String moved : new String[]{"threads", "maxQueue"}) {
          assertNull(name + ": importer." + moved + " moved to job.import* and is now ignored", importer.get(moved));
        }
      }
    }
  }

  @Test
  public void downloadFile() {
    WsServerConfig cfg = new WsServerConfig();
    cfg.job.downloadDir = new File("/tmp/col");
    UUID key = UUID.fromString("7ca06f44-2c0c-4fa9-a410-ac072c378378");
    assertEquals(new File("/tmp/col/7c/7ca06f44-2c0c-4fa9-a410-ac072c378378.zip"), cfg.job.downloadFile(key));
  }

}