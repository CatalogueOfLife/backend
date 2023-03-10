package life.catalogue.release;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;

import static org.junit.Assert.*;

public class XReleaseConfigTest {

  @Test
  public void loadConfig() throws Exception {
    var cfg = XRelease.loadConfig(URI.create("https://raw.githubusercontent.com/CatalogueOfLife/data/master/xcol/xcol-config.yaml"));
    //var cfg = XRelease.loadConfig(new FileInputStream("/Users/markus/code/data/data/xcol/xcol-config.yaml"));
    assertNotNull(cfg);
    assertNotNull(cfg.basionymExclusions);
    assertNotNull(cfg.homonymExclusions);
    assertTrue(cfg.groupBasionyms);
  }
}