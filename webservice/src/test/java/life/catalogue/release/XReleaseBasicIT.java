package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.cache.LatestDatasetKeyCacheImpl;
import life.catalogue.dao.*;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;

import javax.validation.Validation;

import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.google.common.eventbus.EventBus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class XReleaseBasicIT {

  public final static TestDataRule.TestData XRELEASE_DATA = new TestDataRule.TestData("xrelease", 13, 1, 2,
    Map.of(
      "sector", Map.of("created_by", 100, "modified_by", 100)
    ), null);
  final int projectKey = Datasets.COL;


  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  IdProvider provider;
  final NameMatchingRule matchingRule = new NameMatchingRule();
  final SyncFactoryRule syncFactoryRule = new SyncFactoryRule();
  ProjectCopyFactory projectCopyFactory;
  private WsServerConfig cfg;

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(XRELEASE_DATA))
    .around(matchingRule)
    .around(syncFactoryRule);


  @Before
  public void init() throws IOException {
    cfg = new WsServerConfig();
    cfg.apiURI = null;
    cfg.clbURI = URI.create("https://www.dev.checklistbank.org");

    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    provider = new IdProvider(projectKey, 1, -1, cfg.release, factory);

    EventBus bus = mock(EventBus.class);
    ExportManager exm = mock(ExportManager.class);
    DatasetExportDao exDao = mock(DatasetExportDao.class);
    UserDao udao = mock(UserDao.class);
    ReferenceDao rdao = mock(ReferenceDao.class);
    DoiService doiService = mock(DoiService.class);
    DatasetConverter converter = new DatasetConverter(cfg.portalURI, cfg.clbURI, udao::get);
    LatestDatasetKeyCacheImpl lrCache = mock(LatestDatasetKeyCacheImpl.class);
    DoiUpdater doiUpdater = new DoiUpdater(factory, doiService, lrCache, converter);
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    var nuIdxService = NameUsageIndexService.passThru();
    var imgService = ImageService.passThru();
    var diDao = new DatasetImportDao(factory, TreeRepoRule.getRepo());
    var dDao = new DatasetDao(factory, cfg.normalizer, cfg.release, cfg.importer, null, imgService, diDao, exDao, nuIdxService, null, bus, validator);

    projectCopyFactory = new ProjectCopyFactory(null, syncFactoryRule.getMatcher(), SyncFactoryRule.getFactory(),
      syncFactoryRule.getDiDao(), dDao, syncFactoryRule.getSiDao(), rdao, syncFactoryRule.getnDao(), syncFactoryRule.getSdao(),
      exm, nuIdxService, imgService, doiService, doiUpdater, factory, validator, cfg
    );
  }

  @After
  public void destroy() {
    org.apache.commons.io.FileUtils.deleteQuietly(cfg.release.reportDir);
  }


  @Test
  public void release() throws Exception {
    var xrel = projectCopyFactory.buildExtendedRelease(13, Users.TESTER);
    xrel.run();

    assertEquals(ImportState.FINISHED, xrel.getMetrics().getState());

    // assert tree
    InputStream tree = getClass().getResourceAsStream("/assembly-trees/xrelease-expected.tree");
    SectorSyncIT.assertTree(xrel.newDatasetKey, tree);

    // verify secondary sources
    try(SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      VerbatimSourceMapper vsm = session.getMapper(VerbatimSourceMapper.class);

      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      var names = num.listByRegex(xrel.newDatasetKey, xrel.datasetKey, "^Urocyon citrinus", null, null, null, null, new Page());
      assertEquals(1, names.size());
      assertEquals("Stoker, 1887", names.get(0).getAuthorship()); // not Stokker from 101
      var dsid = names.get(0).toDSID(xrel.newDatasetKey);

      // 2 sectors from dataset 101 & 102 have an authorship update for that name
      // make sure we only have one as the secondary source
      var all = vsm.list(dsid);
      assertEquals(1, all.size());

      var src = vsm.getWithSources(dsid);
      assertEquals(100, (int)src.getSourceDatasetKey());
      assertEquals("srcX", src.getSourceId());
      assertEquals(1, src.getSecondarySources().size());
      // sector from dataset 102 has prio over the 101 one, so the author update comes from that
      assertTrue(DSID.equals(DSID.of(102, "x2"), src.getSecondarySources().get(InfoGroup.AUTHORSHIP)));
    }
  }

}