package life.catalogue.release;

import life.catalogue.HttpClientUtils;
import life.catalogue.WsServerConfig;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.dao.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndexFactory;

import java.net.URI;

import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import com.google.common.eventbus.EventBus;

import static org.mockito.Mockito.mock;

public abstract class ProjectBaseIT {
  
  @ClassRule
  public static PgSetupRule pg = new PgSetupRule();

  @ClassRule
  public static final TreeRepoRule treeRepoRule = new TreeRepoRule();

  DatasetImportDao diDao;
  SectorImportDao siDao;
  SectorDao sdao;
  DatasetDao dDao;
  TaxonDao tdao;
  ReleaseManager releaseManager;
  CloseableHttpClient client;

  @Before
  public void init() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.apiURI = URI.create("https://api.dev.catalogue.life");

    EventBus bus = mock(EventBus.class);
    ExportManager exm = mock(ExportManager.class);
    DatasetExportDao exDao = mock(DatasetExportDao.class);
    UserDao udao = mock(UserDao.class);
    DoiService doiService = mock(DoiService.class);
    DatasetConverter converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);
    LatestDatasetKeyCache lrCache = mock(LatestDatasetKeyCache.class);
    DoiUpdater doiUpdater = new DoiUpdater(PgSetupRule.getSqlSessionFactory(), doiService, lrCache, converter);
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    dDao = new DatasetDao(PgSetupRule.getSqlSessionFactory(), null, ImageService.passThru(), diDao, exDao, NameUsageIndexService.passThru(), null, bus);
    siDao = new SectorImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    NameDao nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru());
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru());
    sdao = new SectorDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao);
    tdao.setSectorDao(sdao);
    client = HttpClientUtils.httpsClient();
    releaseManager = new ReleaseManager(client, diDao, dDao, exm, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, doiUpdater, PgSetupRule.getSqlSessionFactory(), cfg);
  }

  @After
  public void shutdown() throws Exception {
    client.close();
  }
  
}