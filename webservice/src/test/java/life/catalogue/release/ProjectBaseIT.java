package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.dao.UserDao;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import java.net.URI;

import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.google.common.eventbus.EventBus;

import static org.mockito.Mockito.mock;

public abstract class ProjectBaseIT {
  
  final static PgSetupRule pg = new PgSetupRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule();
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @ClassRule
  public static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(treeRepoRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  DatasetDao dDao;
  ProjectCopyFactory projectCopyFactory;
  Validator validator;

  @Before
  public void init() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.apiURI = null;
    cfg.clbURI = URI.create("https://www.dev.checklistbank.org");
    EventBus bus = mock(EventBus.class);
    ExportManager exm = mock(ExportManager.class);
    DatasetExportDao exDao = mock(DatasetExportDao.class);
    UserDao udao = mock(UserDao.class);
    DoiService doiService = mock(DoiService.class);
    DatasetConverter converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);
    LatestDatasetKeyCacheImpl lrCache = mock(LatestDatasetKeyCacheImpl.class);
    DoiUpdater doiUpdater = new DoiUpdater(SqlSessionFactoryRule.getSqlSessionFactory(), doiService, lrCache, converter);
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    dDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), cfg.normalizer, cfg.release, null, ImageService.passThru(), syncFactoryRule.getDiDao(), exDao, NameUsageIndexService.passThru(), null, bus, validator);
    projectCopyFactory = new ProjectCopyFactory(null, NameMatchingRule.getIndex(), SyncFactoryRule.getFactory(),
      syncFactoryRule.getDiDao(), dDao, syncFactoryRule.getSiDao(), syncFactoryRule.getnDao(), syncFactoryRule.getSdao(),
      exm, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, doiUpdater, SqlSessionFactoryRule.getSqlSessionFactory(), validator, cfg
    );
  }
  
}