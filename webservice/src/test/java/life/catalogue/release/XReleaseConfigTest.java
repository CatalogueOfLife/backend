package life.catalogue.release;

import java.net.URI;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class XReleaseConfigTest {

  @Test
  public void loadConfig() throws Exception {
    var cfg = XRelease.loadConfig(URI.create("https://raw.githubusercontent.com/CatalogueOfLife/data/master/xcol/xcol-config.yaml"));
    assertNotNull(cfg);
    assertNotNull(cfg.basionymExclusions);
    assertNotNull(cfg.enforceUnique);
    assertTrue(cfg.enforceUnique.containsKey(Rank.GENUS));
    assertTrue(cfg.homotypicConsolidation);
    assertTrue(cfg.sourceDatasetExclusion.contains(6675));

  }
}