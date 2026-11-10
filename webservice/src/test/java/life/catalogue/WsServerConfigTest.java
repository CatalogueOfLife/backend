 package life.catalogue;

 import life.catalogue.common.io.Resources;
 import life.catalogue.dw.auth.map.MapAuthenticationFactory;
 import life.catalogue.dw.managed.Component;
 import life.catalogue.dw.managed.ComponentMode;

 import java.io.ByteArrayInputStream;
 import java.io.File;
 import java.io.FileInputStream;
 import java.io.IOException;
 import java.io.InputStream;
 import java.nio.charset.StandardCharsets;
 import java.util.UUID;

 import org.assertj.core.api.Assertions;
 import org.junit.Test;

 import com.fasterxml.jackson.databind.JsonNode;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

 import io.dropwizard.configuration.ConfigurationParsingException;
 import io.dropwizard.configuration.ConfigurationSourceProvider;
 import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
 import io.dropwizard.configuration.YamlConfigurationFactory;
 import io.dropwizard.jackson.Jackson;
 import io.dropwizard.jersey.validation.Validators;
 import jakarta.validation.Validator;

 import static org.junit.Assert.assertEquals;
 import static org.junit.Assert.assertNotNull;
 import static org.junit.Assert.assertNull;
 import static org.junit.Assert.assertThrows;
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

  /**
   * @return the local environment config of that name, or null if it is not present
   */
  private static File envConfig(String name) {
    File f = new File(name);
    if (!f.exists()) {
      f = new File("webservice", name); // when run from the reactor root
    }
    return f.exists() ? f : null;
  }

  /**
   * Checks the local environment configs against the config classes.
   * The yaml factory silently ignores unknown properties, so a setting that moved in java but was left behind
   * in one of these files would be dropped without a word - which is how an import lane silently falls back to
   * a single thread. Assert on the parsed yaml tree instead of trusting the binding.
   * <p>
   * These files are gitignored (see .gitignore "webservice/config-*.yaml"), so they exist only on a developer
   * machine and this check is skipped entirely on CI and on a fresh clone. It is a local convenience, NOT
   * coverage - the authoritative deploy configs live in the deploy repo and are not verified by anything here.
   */
  @Test
  public void environmentConfigsMatchConfigClasses() throws Exception {
    final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    for (String name : new String[]{"config-prod.yaml", "config-dev.yaml", "config-local.yaml"}) {
      File f = envConfig(name);
      if (f == null) {
        continue; // not a tracked file - absent on CI and fresh clones
      }
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

  /**
   * The per environment component modes bind as a map of deviations, everything unlisted staying AUTO. Worth a test
   * of its own because the yaml factory silently ignores what it cannot bind, so a typo here would not fail a deploy
   * - it would quietly start the very component the environment wanted gone.
   */
  @Test
  public void componentModes() throws Exception {
    final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    WsServerConfig cfg = yaml.readValue("""
      components:
        Feedback: DISABLED
        DoiUpdater: DISABLED
        CronExecutor: MANUAL
        ImportScheduler: MANUAL
      """, WsServerConfig.class);

    assertEquals(ComponentMode.DISABLED, cfg.mode(Component.Feedback));
    assertEquals(ComponentMode.DISABLED, cfg.mode(Component.DoiUpdater));
    assertEquals(ComponentMode.MANUAL, cfg.mode(Component.CronExecutor));
    assertEquals(ComponentMode.MANUAL, cfg.mode(Component.ImportScheduler));
    // anything not listed, and every component at all when the key is absent
    assertEquals(ComponentMode.AUTO, cfg.mode(Component.NamesIndex));
    assertEquals(ComponentMode.AUTO, cfg.mode(Component.JobExecutor));
    assertEquals(ComponentMode.AUTO, new WsServerConfig().mode(Component.Feedback));
  }

  /**
   * The server parses its config with dropwizard's default factory, which unlike the factory above rejects unknown
   * properties. The Chronicle settings of the old event broker are such properties now and must stop a server
   * whose deploy config still carries them.
   */
  @Test
  public void oldBrokerSettingsAreRejected() throws Exception {
    // unknown properties fail the parsing, so no validator is needed - it would only ask for the other required settings
    var strict = new DefaultConfigurationFactoryFactory<WsServerConfig>().create(WsServerConfig.class, null, objectMapper, "dw");
    for (String old : new String[]{"queueDir: /srv/events", "pollingLatency: 10", "poolSize: 2", "bufferSize: 1024"}) {
      String yaml = "broker:\n  name: main\n  " + old + "\n";
      var e = assertThrows(old, ConfigurationParsingException.class,
        () -> strict.build(path -> new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "config.yml")
      );
      assertTrue(e.getMessage(), e.getMessage().contains("Unrecognized field at: broker." + old.substring(0, old.indexOf(':'))));
    }
    WsServerConfig cfg = strict.build(
      path -> new ByteArrayInputStream("broker:\n  name: rw\n".getBytes(StandardCharsets.UTF_8)), "config.yml");
    assertEquals("rw", cfg.broker.name);
  }

  @Test
  public void downloadFile() {
    WsServerConfig cfg = new WsServerConfig();
    cfg.job.downloadDir = new File("/tmp/col");
    UUID key = UUID.fromString("7ca06f44-2c0c-4fa9-a410-ac072c378378");
    assertEquals(new File("/tmp/col/7c/7ca06f44-2c0c-4fa9-a410-ac072c378378.zip"), cfg.job.downloadFile(key));
  }

}