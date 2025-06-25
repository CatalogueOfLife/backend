package life.catalogue.portal;

import life.catalogue.TestUtils;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
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
  final LatestDatasetKeyCache cache = new LatestDatasetKeyCache() {
    @Override
    public void setSqlSessionFactory(SqlSessionFactory factory) {
    }

    @Override
    public @Nullable Integer getLatestRelease(int projectKey, boolean ext) {
      return dataRule.testData.key;
    }

    @Override
    public @Nullable Integer getLatestReleaseCandidate(int projectKey, boolean ext) {
      return dataRule.testData.key;
    }

    @Override
    public @Nullable Integer getReleaseByAttempt(int projectKey, int attempt) {
      return null;
    }

    @Override
    public @Nullable Integer getColRelease(int year, int month, boolean extended) {
      return null;
    }

    @Override
    public @Nullable Integer getDatasetKeyByGbif(UUID gbif) {
      return null;
    }

    @Override
    public boolean isLatestRelease(int datasetKey) {
      return true;
    }

    @Override
    public void refresh(int projectKey) {
    }

    @Override
    public void clear() {
    }
  };

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  PortalPageRenderer renderer;

  @Before
  public void init() throws IOException {
    var dDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, null, TestUtils.mockedBroker());
    var srcDao = new DatasetSourceDao(SqlSessionFactoryRule.getSqlSessionFactory());
    var nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, null);
    var tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, null, null, null);
    var p = Path.of("/tmp/col/templates");
    PathUtils.deleteRecursively(p);
    renderer = new PortalPageRenderer(dDao, srcDao, tDao, cache, p);
  }

  @After
  public void after() throws IOException {
    PathUtils.deleteRecursively(renderer.getPortalTemplateDir());
  }

  @Test
  public void renderTaxon() throws Exception {
    assertEquals(HttpStatus.SC_OK, renderer.renderTaxon("root-1", PROD, false).getStatus());
    assertEquals(HttpStatus.SC_OK, renderer.renderTaxon("root-1", DEV, false).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderTaxon("nope", PROD, false).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderTaxon("nope", DEV, false).getStatus());
  }

  @Test
  public void renderDataset() throws Exception {
    assertEquals(HttpStatus.SC_OK, renderer.renderDatasource(dataRule.testData.key, PROD, false).getStatus());
    assertEquals(HttpStatus.SC_OK, renderer.renderDatasource(dataRule.testData.key, DEV, false).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderDatasource(99999, PROD, false).getStatus());
    assertEquals(HttpStatus.SC_NOT_FOUND, renderer.renderDatasource(99999, DEV, false).getStatus());
  }

  @Test
  public void renderMetadata() throws Exception {
    assertEquals(HttpStatus.SC_OK, renderer.renderMetadata(PROD, false).getStatus());
  }

  @Test
  public void store() throws Exception {
    renderer.store(PROD, PortalPageRenderer.PortalPage.DATASET, "Hergott Sackra nochamol.");
    assertEquals("Hergott Sackra nochamol.", renderer.renderDatasource(dataRule.testData.key, PROD, false).getEntity());

    renderer.store(PROD, PortalPageRenderer.PortalPage.DATASET, "Hergott Sackra nochamol. ${freemarker!\"no\"} works");
    assertEquals("Hergott Sackra nochamol. no works", renderer.renderDatasource(dataRule.testData.key, PROD, false).getEntity());

    renderer.store(PREVIEW, PortalPageRenderer.PortalPage.DATASET, "Hergott catalogueKey: '2351' , pathToTree: '/data/browse', auth: '', pathToSearch: '/data/search', pageTitleTemplate: 'COL | __dataset__'");
    assertEquals("Hergott catalogueKey: '2351' , pathToTree: '/data/browse', auth: '', pathToSearch: '/data/search', pageTitleTemplate: 'COL | __dataset__'", renderer.renderDatasource(dataRule.testData.key, PREVIEW, false).getEntity());

    renderer.store(PROD, PortalPageRenderer.PortalPage.METADATA, "Hergott Sackra nochamol. ${freemarker!\"no\"} works");
    assertEquals("Hergott Sackra nochamol. no works", renderer.renderMetadata(PROD, false).getEntity());
  }
}