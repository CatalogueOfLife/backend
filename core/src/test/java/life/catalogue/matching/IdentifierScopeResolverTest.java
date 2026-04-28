package life.catalogue.matching;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.config.IdentifierScopeConfig;

import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.junit.DatasetInfoCacheMockRule;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class IdentifierScopeResolverTest {

  private IdentifierScopeConfig cfg;
  private IdentifierScopeResolver resolver;
  @Rule
  public DatasetInfoCacheMockRule infocacheRule = new DatasetInfoCacheMockRule();

  @Before
  public void setUp() {
    cfg = new IdentifierScopeConfig();
    cfg.mapping.put("col", 3);
    cfg.mapping.put("wfo", 2030);
    resolver = new IdentifierScopeResolver(cfg); // no DB lookups in these tests
  }

  @Test
  public void resolveByDatasetAndSourceKey() {
    // project itself: sourceKey is null
    assertEquals("col", resolver.resolve(3));
    // unmapped dataset
    assertNull(resolver.resolve(12345));
    // unmapped release whose project is also unmapped
    assertNull(resolver.resolve(99));
  }

  @Test
  public void resolveByDataset() {
    Dataset project = new Dataset();
    project.setKey(3);
    project.setOrigin(DatasetOrigin.PROJECT);
    project.setType(DatasetType.TAXONOMIC);
    assertEquals("col", resolver.resolve(project));

    Dataset release = new Dataset();
    release.setKey(105);
    release.setSourceKey(3);
    release.setOrigin(DatasetOrigin.RELEASE);
    release.setType(DatasetType.TAXONOMIC);
    assertEquals("col", resolver.resolve(release));

    Dataset external = new Dataset();
    external.setKey(2030);
    external.setOrigin(DatasetOrigin.EXTERNAL);
    external.setType(DatasetType.TAXONOMIC);
    assertEquals("wfo", resolver.resolve(external));

    assertNull(resolver.resolve((Dataset) null));
  }

  @Test
  public void configValidationDropsUnknownScopes() {
    IdentifierScopeConfig c = new IdentifierScopeConfig();
    c.mapping.put("col", 3);           // case differs - should be normalised
    c.mapping.put("not-a-scope", 7);   // unknown - should be dropped
    c.mapping.put(null, 8);            // null - should be dropped
    c.mapping.put("worms", null);      // null - should be dropped
    c.mapping.put("ITIS", 321);        // not lower case
    c.validate();
    assertEquals(1, c.mapping.size());
    assertEquals(Datasets.COL, (int) c.mapping.get("col"));
    assertEquals("col", c.mapping.inverse().get(3));
    assertFalse(c.mapping.containsKey(7));
    assertFalse(c.mapping.containsKey(8));
  }
}
