package life.catalogue.release;

import java.net.URI;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class XReleaseConfigTest {

  @Test
  public void loadConfig() throws Exception {
    var cfg = XRelease.loadConfig(URI.create("https://raw.githubusercontent.com/CatalogueOfLife/data/master/xcol/xcol-config.yaml"));
    assertNotNull(cfg);
    assertNotNull(cfg.basionymExclusions);
    assertNotNull(cfg.homonymExclusions);
    assertTrue(cfg.homonymExclusions.isEmpty());
    assertTrue(cfg.homotypicConsolidation);
  }
}