package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSyncMergeIT;
import life.catalogue.assembly.SectorSyncTestBase;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.common.util.YamlUtils;
import life.catalogue.dao.*;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.junit.TreeRepoRule;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.UsageMatcherGlobal;
import life.catalogue.junit.TxtTreeDataRule;

import org.apache.ibatis.io.Resources;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.*;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Testing XReleases with a set of text tree sources as main release and merge sectors.
 * The test is parameterized to represent one test combination.
 */
@RunWith(Parameterized.class)
public class XReleaseIT extends SectorSyncTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(XReleaseIT.class);

  final static SqlSessionFactoryRule pg = new PgSetupRule(); // PgConnectionRule("col", "postgres", "postgres");
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
      .outerRule(pg)
      .around(treeRepoRule);

  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  CloseableHttpClient hc;
  ProjectCopyFactory projectCopyFactory;

  @Rule
  public final TestRule testRules = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);

  int testNum = 0;
  String project;
  List<String> sources;
  SectorSyncMergeIT.ProjectTestInfo info;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"target", List.of(
        "lpsn"
      )},
      {"dupe-genera", List.of(
        "src1",
        "src2"
      )},
      {"ex-authors", List.of(
        "worms",
        "uksi"
      )},
      {"inverse_ranks", List.of(
        "repdb"
      )},
      {"abronia", List.of(
        "itis",
        "wcvp",
        "repdb"
      )},
      {"unplaced_synonyms", List.of(
        "wcvp"
      )},
      {"incertae",List.of(
        "sedis",
        "src2",
        "nomen"
      )},
      {"homonyms", List.of(
        "worms",
        "itis",
        "wcvp",
        "taxref",
        "ala",
        "ipni",
        "irmng"
      )}
    });
  }

  public XReleaseIT(String project, List<String> sources) {
    this.project = project.toLowerCase();
    this.sources = sources;
  }

  @Before
  public void init () throws Throwable {
    LOG.info("Setup sync & release factory");
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    final SqlSessionFactory factory = SqlSessionFactoryRule.getSqlSessionFactory();
    var diDao = new DatasetImportDao(factory, TreeRepoRule.getRepo());
    var siDao = new SectorImportDao(factory, TreeRepoRule.getRepo());
    var eDao = mock(EstimateDao.class);
    var nDao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.passThru(), validator);
    var tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, NameUsageIndexService.passThru(), validator);
    var sdao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao, validator);
    tdao.setSectorDao(sdao);
    var matcher = new UsageMatcherGlobal(NameMatchingRule.getIndex(), UsageCache.hashMap(), SqlSessionFactoryRule.getSqlSessionFactory());
    var syncFactory = new SyncFactory(SqlSessionFactoryRule.getSqlSessionFactory(), NameMatchingRule.getIndex(), matcher, sdao, siDao, eDao, NameUsageIndexService.passThru(), new EventBus("test-bus"));
    var cfg = new WsServerConfig();

    hc = HttpClientBuilder.create().build();
    var du = new DownloadUtil(hc);
    var dDao = new DatasetDao(factory, du, diDao, validator);
    var rDao = mock(ReferenceDao.class);
    var exportManager = mock(ExportManager.class);
    var doiService = mock(DoiService.class);
    var doiUpdater = mock(DoiUpdater.class);
    projectCopyFactory = new ProjectCopyFactory(hc, matcher, syncFactory, diDao, dDao, siDao, rDao, nDao, sdao,
      exportManager, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, doiUpdater,
      SqlSessionFactoryRule.getSqlSessionFactory(), validator, cfg
    );

    // set project default settings
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var settings = dm.getSettings(Datasets.COL);
      settings.put(Setting.SECTOR_NAME_TYPES, List.of(NameType.SCIENTIFIC, NameType.VIRUS, NameType.HYBRID_FORMULA));
      settings.put(Setting.SECTOR_ENTITIES, List.of(EntityType.NAME_USAGE, EntityType.VERNACULAR, EntityType.REFERENCE));
      dm.updateSettings(Datasets.COL, settings, Users.TESTER);
    }

    // load text trees & create sectors
    info = SectorSyncMergeIT.setupProject(project, sources);
  }

  @Test
  public void syncAndCompare() throws Throwable {
    LOG.info("Project {}. Trees: {}", project, sources);
    testNum++;

    // rematch
    matchingRule.rematchAll();

    // regular release
    var rel = projectCopyFactory.buildRelease(Datasets.COL, Users.RELEASER);
    var job = new Thread(rel);
    job.run();
    final int releaseKey = rel.getNewDatasetKey();
    System.out.println("\n*** RELEASED " + releaseKey + " ***");
    assertSameTree(Datasets.COL, releaseKey);
    // poor mans release publishing
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var d = dm.get(releaseKey);
      d.setPrivat(false);
      dm.update(d);
    }

    // extended release
    XRelease xrel = projectCopyFactory.buildExtendedRelease(releaseKey, Users.RELEASER);
    xrel.setCfg(info.cfg);
    job = new Thread(xrel);
    job.run();
    final int xreleaseKey = xrel.getNewDatasetKey();
    System.out.println("\n*** XRELEASED " + xreleaseKey + " ***");

    System.out.println("\n*** COMPARISON ***");
    // compare with expected tree
    assertTree(project, xreleaseKey, getClass().getResourceAsStream("/txtree/" + project + "/xrelease.txtree"));

    conditionalChecks(xrel);
  }

  private void conditionalChecks(XRelease xrel) {
    if (project.equals("inverse_ranks")) {
      // wrong rank order issue
      final DSID<String> key = DSID.root(xrel.newDatasetKey);
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        var num = session.getMapper(NameUsageMapper.class);
        var vm = session.getMapper(VerbatimSourceMapper.class);
        var res = num.findOne(xrel.newDatasetKey, Rank.FAMILY, "Anguidae");
        assertNotNull(res);
        var v = vm.get(key.id(res.getId()));
        assertTrue(v.getIssues().contains(Issue.CLASSIFICATION_RANK_ORDER_INVALID));
      }
    }
  }

  @After
  public void teardown() throws Exception {
    hc.close();
  }
}