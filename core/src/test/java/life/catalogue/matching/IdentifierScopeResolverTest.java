package life.catalogue.matching;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.config.IdentifierScopeConfig;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class IdentifierScopeResolverTest {

  private IdentifierScopeConfig cfg;
  private IdentifierScopeResolver resolver;

  @Before
  public void setUp() {
    cfg = new IdentifierScopeConfig();
    cfg.mapping.put(3, "col");
    cfg.mapping.put(2030, "wfo");
    resolver = new IdentifierScopeResolver(cfg, null); // no DB lookups in these tests
  }

  @Test
  public void resolveByDatasetAndSourceKey() {
    // project itself: sourceKey is null
    assertEquals("col", resolver.resolve(3, null));
    // a release of project 3: sourceKey points to the project
    assertEquals("col", resolver.resolve(99, 3));
    // unmapped dataset
    assertNull(resolver.resolve(12345, null));
    // unmapped release whose project is also unmapped
    assertNull(resolver.resolve(99, 12345));
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
  public void effectiveKeyHelper() {
    assertEquals(3, IdentifierScopeResolver.effectiveKey(3, null));
    assertEquals(3, IdentifierScopeResolver.effectiveKey(99, 3));
    assertEquals(2030, IdentifierScopeResolver.effectiveKey(2030, null));
  }

  @Test
  public void configValidationDropsUnknownScopes() {
    IdentifierScopeConfig c = new IdentifierScopeConfig();
    c.mapping.put(3, "COL");           // case differs - should be normalised
    c.mapping.put(7, "not-a-scope");   // unknown - should be dropped
    c.mapping.put(8, null);            // null - should be dropped
    c.validate();
    assertEquals(1, c.mapping.size());
    assertEquals("col", c.mapping.get(3));
    assertFalse(c.mapping.containsKey(7));
    assertFalse(c.mapping.containsKey(8));
  }
}
