package life.catalogue.release;

import java.net.URI;

import life.catalogue.api.vocab.DatasetType;

import life.catalogue.config.ReleaseConfig;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReleaseConfigTest {

  @Test
  public void testCfgBase() throws Exception {
    var cfg = ProjectRelease.loadConfig(ProjectReleaseConfig.class, URI.create("https://raw.githubusercontent.com/CatalogueOfLife/data/refs/heads/master/release-config.yaml"), true);

    assertNotNull(cfg);
    assertEquals("COL{date,yy.M}", cfg.metadata.alias);
    assertNotNull(cfg.metadata.title);
    assertNotNull(cfg.metadata.description);
    assertNotNull(cfg.metadata.publisher);
    assertNotNull(cfg.metadata.contact);
    assertTrue(cfg.metadata.creator.size() > 10);
    assertTrue(cfg.metadata.additionalCreators.size() > 20);
    assertTrue(cfg.metadata.addSourceAuthors);
    assertNull(cfg.metadata.authorSourceExclusion);
  }

  @Test
  public void testCfgXR() throws Exception {
    var cfg = ProjectRelease.loadConfig(XReleaseConfig.class, URI.create("https://raw.githubusercontent.com/CatalogueOfLife/data/refs/heads/master/xrelease/xrelease-config.yaml"), true);

    //File cfgFile = new File("/Users/markus/code/data/data/xrelease/xrelease-config.yaml");
    //cfg = YamlUtils.read(XReleaseConfig.class, cfgFile);

    assertNotNull(cfg);
    assertNotNull(cfg.basionymExclusions);
    assertNotNull(cfg.enforceUnique);
    assertTrue(cfg.enforceUnique.containsKey(Rank.GENUS));
    assertTrue(cfg.homotypicConsolidation);
    assertTrue(cfg.sourceDatasetExclusion.contains(6675));
    assertEquals("COL{date,yy.M} XR", cfg.metadata.alias);
    assertNotNull(cfg.metadata.title);
    assertNotNull(cfg.metadata.description);
    assertNotNull(cfg.metadata.publisher);
    assertNotNull(cfg.metadata.contact);
    assertTrue(cfg.metadata.creator.size() > 10);
    assertTrue(cfg.metadata.additionalCreators.size() > 20);
    assertTrue(cfg.metadata.addSourceAuthors);
    assertTrue(cfg.metadata.authorSourceExclusion.contains(DatasetType.ARTICLE));
    assertFalse(cfg.decisions.isEmpty());
  }
}