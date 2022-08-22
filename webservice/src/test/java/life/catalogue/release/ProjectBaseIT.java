package life.catalogue.release;

import life.catalogue.HttpClientUtils;
import life.catalogue.WsServerConfig;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
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

import javax.validation.Validation;
import javax.validation.Validator;

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
  EstimateDao eDao;
  SectorDao sdao;
  DatasetDao dDao;
  NameDao nDao;
  TaxonDao tdao;
  Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  ProjectCopyFactory projectCopyFactory;
  CloseableHttpClient client;

  @Before
  public void init() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.apiURI = URI.create("https://api.dev.catalogue.life");
    cfg.clbURI = URI.create("https://data.dev.catalogue.life");
    EventBus bus = mock(EventBus.class);
    ExportManager exm = mock(ExportManager.class);
    DatasetExportDao exDao = mock(DatasetExportDao.class);
    UserDao udao = mock(UserDao.class);
    DoiService doiService = mock(DoiService.class);
    DatasetConverter converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);
    LatestDatasetKeyCacheImpl lrCache = mock(LatestDatasetKeyCacheImpl.class);
    DoiUpdater doiUpdater = new DoiUpdater(PgSetupRule.getSqlSessionFactory(), doiService, lrCache, converter);
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    dDao = new DatasetDao(100, PgSetupRule.getSqlSessionFactory(), cfg.normalizer, cfg.release, null, ImageService.passThru(), diDao, exDao, NameUsageIndexService.passThru(), null, bus, validator);
    siDao = new SectorImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    eDao = new EstimateDao(PgSetupRule.getSqlSessionFactory(), validator);
    nDao = new NameDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    tdao = new TaxonDao(PgSetupRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    sdao = new SectorDao(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao, validator);
    tdao.setSectorDao(sdao);
    client = HttpClientUtils.httpsClient();
    projectCopyFactory = new ProjectCopyFactory(client, NameIndexFactory.passThru(), diDao, dDao, siDao, nDao, sdao, exm, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, doiUpdater, PgSetupRule.getSqlSessionFactory(), validator, cfg);
  }

  @After
  public void shutdown() throws Exception {
    if (client != null) {
      client.close();
    }
  }
  
}