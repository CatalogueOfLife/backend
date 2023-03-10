package life.catalogue.release;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class XReleaseTest {

  @Test
  public void loadConfig() {
    var cfg = XRelease.loadConfig(URI.create("https://raw.githubusercontent.com/CatalogueOfLife/data/master/xcol/xcol-config.yaml"));
    assertNotNull(cfg);
    assertNotNull(cfg.basionymExclusions);
    assertNotNull(cfg.homonymExclusions);
    assertTrue(cfg.groupBasionyms);
  }
}