package life.catalogue.portal;

import life.catalogue.TestUtils;
import life.catalogue.api.model.Dataset;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.common.io.PathUtils;
import life.catalogue.common.text.StringUtils;
import life.catalogue.dao.*;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import org.apache.hc.core5.http.HttpStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.*;

import static life.catalogue.portal.PortalPageRenderer.Environment.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PortalPageRendererIT {

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  PortalPageRenderer renderer;

  @Before
  public void init() throws IOException {
    DatasetInfoCache.CACHE.setFactory(SqlSessionFactoryRule.getSqlSessionFactory());
    var dDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, null, TestUtils.mockedBroker());
    var srcDao = new DatasetSourceDao(SqlSessionFactoryRule.getSqlSessionFactory());
    var nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, null);
    var tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, null, null, null, null);
    var p = Path.of("/tmp/col/templates");
    PathUtils.deleteRecursively(p);
    renderer = new PortalPageRenderer(dDao, srcDao, tDao, p, false);
    loadTemplates(renderer, dataRule.testData.key);
  }

  static void loadTemplates(PortalPageRenderer renderer, int releaseKey) throws IOException {
    for (PortalPageRenderer.Environment env : PortalPageRenderer.Environment.values()) {
      renderer.setReleaseKey(env, releaseKey);
      for (PortalPageRenderer.PortalPage pp : PortalPageRenderer.PortalPage.values()) {
        try (var in = PortalPageRenderer.class.getResourceAsStream("/portal-ftl/"+pp.name()+".ftl")) {
          renderer.store(env, pp, InputStreamUtils.readEntireStream(in));
        }
      }
    }
  }

  @After
  public void after() throws IOException {
    PathUtils.deleteRecursively(renderer.getPortalTemplateDir());
  }

  @Test
  public void renderTaxon() throws Exception {
    assertEquals(HttpStatus.SC_OK, renderer.renderTaxon("root-1", PROD).getStatus());
    assertEquals(HttpStatus.SC_OK, renderer.renderTaxon("root-1", DEV).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderTaxon("nope", PROD).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderTaxon("nope", DEV).getStatus());
  }

  @Test
  public void renderDataset() throws Exception {
    assertEquals(HttpStatus.SC_OK, renderer.renderDatasource(dataRule.testData.key, PROD).getStatus());
    assertEquals(HttpStatus.SC_OK, renderer.renderDatasource(dataRule.testData.key, DEV).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderDatasource(99999, PROD).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderDatasource(99999, DEV).getStatus());
  }

  @Test
  public void renderMetadata() throws Exception {
    assertEquals(HttpStatus.SC_OK, renderer.renderMetadata(PROD).getStatus());
  }

  @Test
  public void store() throws Exception {
    renderer.store(PROD, PortalPageRenderer.PortalPage.DATASET, "Hergott Sackra nochamol.");
    assertEquals("Hergott Sackra nochamol.", renderer.renderDatasource(dataRule.testData.key, PROD).getEntity());

    renderer.store(PROD, PortalPageRenderer.PortalPage.DATASET, "Hergott Sackra nochamol. ${freemarker!\"no\"} works");
    assertEquals("Hergott Sackra nochamol. no works", renderer.renderDatasource(dataRule.testData.key, PROD).getEntity());

    renderer.store(PREVIEW, PortalPageRenderer.PortalPage.DATASET, "Hergott catalogueKey: '2351' , pathToTree: '/data/browse', auth: '', pathToSearch: '/data/search', pageTitleTemplate: 'COL | __dataset__'");
    assertEquals("Hergott catalogueKey: '2351' , pathToTree: '/data/browse', auth: '', pathToSearch: '/data/search', pageTitleTemplate: 'COL | __dataset__'", renderer.renderDatasource(dataRule.testData.key, PREVIEW).getEntity());

    renderer.store(PROD, PortalPageRenderer.PortalPage.METADATA, "Hergott Sackra nochamol. ${freemarker!\"no\"} works");
    assertEquals("Hergott Sackra nochamol. no works", renderer.renderMetadata(PROD).getEntity());
  }
}