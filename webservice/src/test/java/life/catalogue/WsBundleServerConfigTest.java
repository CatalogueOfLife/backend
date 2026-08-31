package life.catalogue;

import life.catalogue.common.io.Resources;
import life.catalogue.dw.auth.map.MapAuthenticationFactory;

import java.io.IOException;
import java.io.InputStream;

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
    assertEquals("/data/nidx", cfg.namesIndex.file.getPath());
    assertEquals("/data/metrics", cfg.metricsRepo.getPath());
    assertFalse(cfg.namesIndex.verification);
    assertEquals("/data/img", cfg.img.repo.toString());
  }
}
