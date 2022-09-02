package life.catalogue.release;

import life.catalogue.HttpClientUtils;
import life.catalogue.WsServerConfig;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.dao.*;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.importer.PgImportRule;
import life.catalogue.matching.NameIndexFactory;

import java.net.URI;

import javax.validation.Validation;
import javax.validation.Validator;

import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;

import com.google.common.eventbus.EventBus;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.mockito.Mockito.mock;

public abstract class ProjectBaseIT {
  
  final static PgSetupRule pg = new PgSetupRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(treeRepoRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  DatasetDao dDao;
  ProjectCopyFactory projectCopyFactory;
  CloseableHttpClient client;
  Validator validator;

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
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    dDao = new DatasetDao(100, PgSetupRule.getSqlSessionFactory(), cfg.normalizer, cfg.release, null, ImageService.passThru(), syncFactoryRule.getDiDao(), exDao, NameUsageIndexService.passThru(), null, bus, validator);
    client = HttpClientUtils.httpsClient();
    projectCopyFactory = new ProjectCopyFactory(client, NameMatchingRule.getIndex(), syncFactoryRule.getSyncFactory(),
      syncFactoryRule.getDiDao(), dDao, syncFactoryRule.getSiDao(), syncFactoryRule.getnDao(), syncFactoryRule.getSdao(),
      exm, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, doiUpdater, PgSetupRule.getSqlSessionFactory(), validator, cfg
    );
  }

  @After
  public void shutdown() throws Exception {
    if (client != null) {
      client.close();
    }
  }
  
}