package life.catalogue.portal;

import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import org.apache.http.HttpStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

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
    public @Nullable Integer getColAnnualRelease(int year, boolean ext) {
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
  };

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  PortalPageRenderer renderer;

  @Before
  public void init() throws IOException {
    var dDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, null);
    var srcDao = new DatasetSourceDao(SqlSessionFactoryRule.getSqlSessionFactory());
    var nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, null);
    var tDao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, null);
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

    renderer.store(PROD, PortalPageRenderer.PortalPage.CLB_DATASET, "Can I get some SEO please?");
    assertEquals("Can I get some SEO please?", renderer.renderClbDataset(3, PROD).getEntity());

    renderer.store(PROD, PortalPageRenderer.PortalPage.CLB_DATASET, "Can I get some <!-- REPLACE_WITH_SEO --> please?");
    assertNotEquals("Can I get some SEO please?", renderer.renderClbDataset(3, PROD).getEntity());
  }
}