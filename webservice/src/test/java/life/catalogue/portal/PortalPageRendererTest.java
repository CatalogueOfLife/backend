package life.catalogue.portal;

import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.*;

public class PortalPageRendererTest {
  final LatestDatasetKeyCache cache = new LatestDatasetKeyCache() {
    @Override
    public void setSqlSessionFactory(SqlSessionFactory factory) {
    }

    @Override
    public @Nullable Integer getLatestRelease(@NonNull Integer key) {
      return dataRule.testData.key;
    }

    @Override
    public @Nullable Integer getLatestReleaseCandidate(@NonNull Integer key) {
      return dataRule.testData.key;
    }

    @Override
    public @Nullable Integer getReleaseAttempt(@NonNull ReleaseAttempt key) {
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
    renderer = new PortalPageRenderer(srcDao, tDao, cache, new File("/tmp/col/templates"));
  }

  @After
  public void after() throws IOException {
    FileUtils.deleteQuietly(renderer.getPortalTemplateDir());
  }

  @Test
  public void renderTaxon() throws Exception {
    System.out.println(renderer.renderTaxon("root-1", false));
    System.out.println(renderer.renderTaxon("root-1", true));
  }

  @Test
  public void renderDataset() throws Exception {
    System.out.println(renderer.renderDatasource(dataRule.testData.key, false));
    System.out.println(renderer.renderDatasource(dataRule.testData.key, true));
  }

  @Test
  public void render404() throws Exception {
    System.out.println(renderer.render404());
  }
}