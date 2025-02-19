package life.catalogue.release;

import java.io.File;
import java.net.URI;

import life.catalogue.common.util.YamlUtils;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class XReleaseConfigTest {

  @Test
  public void loadConfig() throws Exception {
    var cfg = XRelease.loadConfig(XReleaseConfig.class, URI.create("https://raw.githubusercontent.com/CatalogueOfLife/data/refs/heads/master/xrelease/xrelease-config.yaml"));

    //File cfgFile = new File("/Users/markus/code/data/data/xrelease/xrelease-config.yaml");
    //cfg = YamlUtils.read(XReleaseConfig.class, cfgFile);

    assertNotNull(cfg);
    assertNotNull(cfg.basionymExclusions);
    assertNotNull(cfg.enforceUnique);
    assertTrue(cfg.enforceUnique.containsKey(Rank.GENUS));
    assertTrue(cfg.homotypicConsolidation);
    assertTrue(cfg.sourceDatasetExclusion.contains(6675));
    assertEquals("COL-{date,yyyy-MM-dd}", cfg.metadata.alias);
    assertEquals(1, cfg.decisions.size());

  }
}