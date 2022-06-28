package life.catalogue.portal;

import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.http.HttpStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.*;

import static life.catalogue.portal.PortalPageRenderer.Environment.*;
import static org.junit.Assert.assertEquals;

public class PortalPageRendererTest {
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
    var srcDao = new DatasetSourceDao(PgSetupRule.getSqlSessionFactory());
    var nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), null, null, null);
    var tDao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, null, null);
    renderer = new PortalPageRenderer(srcDao, tDao, cache, Path.of("/tmp/col/templates"));
  }

  @After
  public void after() throws IOException {
    PathUtils.deleteQuietly(renderer.getPortalTemplateDir());
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
  public void store() throws Exception {
    renderer.store(PROD, PortalPageRenderer.PortalPage.DATASET, "Hergott Sackra nochamol.");
    assertEquals("Hergott Sackra nochamol.", renderer.renderDatasource(dataRule.testData.key, PROD).getEntity());

    renderer.store(PROD, PortalPageRenderer.PortalPage.DATASET, "Hergott Sackra nochamol. ${freemarker!\"no\"} works");
    assertEquals("Hergott Sackra nochamol. no works", renderer.renderDatasource(dataRule.testData.key, PROD).getEntity());

    renderer.store(PREVIEW, PortalPageRenderer.PortalPage.DATASET, "Hergott catalogueKey: '2351' , pathToTree: '/data/browse', auth: '', pathToSearch: '/data/search', pageTitleTemplate: 'COL | __dataset__'");
    assertEquals("Hergott catalogueKey: '2351' , pathToTree: '/data/browse', auth: '', pathToSearch: '/data/search', pageTitleTemplate: 'COL | __dataset__'", renderer.renderDatasource(dataRule.testData.key, PREVIEW).getEntity());
  }
}