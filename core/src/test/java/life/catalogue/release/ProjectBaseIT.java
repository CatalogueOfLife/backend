package life.catalogue.release;

import life.catalogue.TestConfigs;
import life.catalogue.TestUtils;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetExportDao;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.dao.UserDao;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.event.EventBroker;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TreeRepoRule;

import java.net.URI;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.mockito.Mockito.mock;

public abstract class ProjectBaseIT {
  
  final static SqlSessionFactoryRule pg = new PgSetupRule();
  //final static SqlSessionFactoryRule pg = new PgConnectionRule("clb", "postgres", "postgres");
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  // this contains the names index being used by syncs and project jobs / releases - use only this instance !!!
  final static NameMatchingRule matchingRule = new NameMatchingRule(true);
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
    TestConfigs cfg = TestConfigs.build();
    cfg.apiURI = null;
    cfg.clbURI = URI.create("https://www.dev.checklistbank.org");
    EventBroker bus = TestUtils.mockedBroker();
    ExportManager exm = mock(ExportManager.class);
    DatasetExportDao exDao = mock(DatasetExportDao.class);
    UserDao udao = mock(UserDao.class);
    ReferenceDao rdao = mock(ReferenceDao.class);
    DoiService doiService = mock(DoiService.class);
    DatasetConverter converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);
    LatestDatasetKeyCacheImpl lrCache = mock(LatestDatasetKeyCacheImpl.class);
    DoiUpdater doiUpdater = new DoiUpdater(SqlSessionFactoryRule.getSqlSessionFactory(), doiService, lrCache, converter);
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    dDao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), cfg.normalizer, cfg.release, cfg.gbif,
      null, ImageService.passThru(), syncFactoryRule.getDiDao(), exDao, NameUsageIndexService.passThru(), null, bus, validator
    );
    projectCopyFactory = new ProjectCopyFactory(null, syncFactoryRule.getMatcher(), SyncFactoryRule.getFactory(),
      syncFactoryRule.getDiDao(), dDao, syncFactoryRule.getSiDao(), rdao, syncFactoryRule.getnDao(), syncFactoryRule.getSdao(),
      exm, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, doiUpdater, SqlSessionFactoryRule.getSqlSessionFactory(), validator,
      cfg.release, cfg.doi, cfg.apiURI, cfg.clbURI
    );
  }
  
}