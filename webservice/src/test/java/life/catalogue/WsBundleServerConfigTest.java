package life.catalogue;

import life.catalogue.common.io.Resources;
import life.catalogue.dw.auth.map.MapAuthenticationFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.YamlConfigurationFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import jakarta.validation.Validator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WsBundleServerConfigTest {
  private static final ObjectMapper objectMapper = Jackson.newObjectMapper();
  private static final Validator validator = Validators.newValidator();
  private static final YamlConfigurationFactory<WsBundleServerConfig> factory =
    new YamlConfigurationFactory<>(WsBundleServerConfig.class, validator, objectMapper, "dw");

  static WsBundleServerConfig read() throws Exception {
    return factory.build(new ConfigurationSourceProvider() {
      @Override
      public InputStream open(String path) throws IOException {
        return Resources.stream(path);
      }
    }, "config-bundle-test.yaml");
  }

  /**
   * The config template BundleBuildCmd bakes into every artifact must parse and validate as a real bundle
   * config, or every bundle we ship is unstartable.
   */
  @Test
  public void shippedTemplateIsValid() throws Exception {
    String tmpl = new String(Resources.stream("life/catalogue/bundle/config.yml").readAllBytes(), StandardCharsets.UTF_8)
      .replace("{{RELEASE_KEY}}", "3287");
    assertFalse("unsubstituted placeholder in the config template", tmpl.contains("{{"));
    File f = File.createTempFile("bundle-config", ".yaml");
    try {
      Files.writeString(f.toPath(), tmpl);
      var cfg = new YamlConfigurationFactory<>(WsBundleServerConfig.class, validator, objectMapper, "dw")
        .build(f);
      assertEquals(3287, cfg.releaseKey);
      assertNotNull("elastic is mandatory for a bundle", cfg.es);
      assertFalse(cfg.es.isEmpty());
      assertEquals("/data/matcher", cfg.matching.storageDir.getPath());
    // jackson replaces the whole nested matching object, so a yaml with a matching block loses the
    // constructor default - the shipped template therefore has to say it, and WsBundleServer enforces it
    assertEquals(0, cfg.matching.pgMatcherThreshold);
    } finally {
      f.delete();
    }
  }

  /**
   * A bundle yaml only ever states the release key and where postgres and elastic live.
   * Everything a bundle does not have - a GBIF registry to authenticate against, a mail server - must come
   * from the defaults, or the config would not validate at all.
   */
  @Test
  public void minimalConfigValidates() throws Exception {
    var cfg = read();
    assertEquals(3287, cfg.releaseKey);
    assertTrue(cfg.indexOnStart);

    assertNotNull(cfg.auth);
    assertTrue(cfg.auth instanceof MapAuthenticationFactory);
    assertTrue(((MapAuthenticationFactory) cfg.auth).users.isEmpty());

    assertNotNull(cfg.mail);
    assertNull("a bundle has no mail server", cfg.mail.host);
    assertNotNull(cfg.mail.from);

    // elastic is mandatory for a bundle
    assertNotNull(cfg.es);
    assertFalse(cfg.es.isEmpty());
    assertEquals("col", cfg.es.index.name);

    // the shipped data volume
    assertEquals("/data/matcher", cfg.matching.storageDir.getPath());
    // jackson replaces the whole nested matching object, so a yaml with a matching block loses the
    // constructor default - the shipped template therefore has to say it, and WsBundleServer enforces it
    assertEquals(0, cfg.matching.pgMatcherThreshold);
    assertEquals("/data/nidx", cfg.namesIndex.file.getPath());
    assertEquals("/data/metrics", cfg.metricsRepo.getPath());
    assertFalse(cfg.namesIndex.verification);
    assertEquals("/data/img", cfg.img.repo.toString());
  }
}
