package life.catalogue.release;

import com.google.common.eventbus.EventBus;

import life.catalogue.WsServer;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SectorSyncTestBase;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.dao.*;
import life.catalogue.db.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.tree.TxtTreeDataRule;

import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.UsageMatcherGlobal;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Validation;
import javax.validation.Validator;

import java.util.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Testing XReleases with a set of text tree sources as main release and merge sectors.
 * The test is parameterized to represent one test combination.
 */
@RunWith(Parameterized.class)
public class XReleaseIT extends SectorSyncTestBase {
  private static final Logger LOG = LoggerFactory.getLogger(XReleaseIT.class);

  final static SqlSessionFactoryRule pg = new PgConnectionRule("col", "postgres", "postgres"); // PgSetupRule(); //
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
  List<String> trees;
  List<Sector> sectors = new ArrayList<>();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      {"homonyms", List.of("worms", "itis", "wcvp", "taxref", "ala", "ipni", "irmng")}
    });
  }

  public XReleaseIT(String project, List<String> trees) {
    this.project = project.toLowerCase();
    this.trees = trees;
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
  }

  @Test
  public void syncAndCompare() throws Throwable {
    LOG.info("Project {}. Trees: {}", project, trees);
    testNum++;
    // load text trees & create sectors
    Map<Integer, String> data = new HashMap<>();
    data.put(Datasets.COL, "txtree/"+project + "/project.txtree");

    int dkey = 100;
    for (String tree : trees) {
      data.put(dkey, "txtree/"+project + "/" + tree.toLowerCase()+".txtree");
      Sector s = new Sector();
      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(dkey);
      s.setMode(Sector.Mode.MERGE);
      s.setPriority(dkey-99);
      s.setNote(tree);
      sectors.add(s);
      dkey++;
    }

    // load source datasets
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(data)) {
      treeRule.setOrigin(DatasetOrigin.EXTERNAL);
      treeRule.before();
    }

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      for (var s : sectors) {
        s.applyUser(Users.TESTER);
        sm.create(s);
      }
    }
    // rematch
    matchingRule.rematchAll();

    // regular release
    var rel = projectCopyFactory.buildRelease(Datasets.COL, Users.RELEASER);
    final int releaseKey = rel.getNewDatasetKey();
    System.out.println("\n*** RELEASE " + releaseKey + " ***");
    var job = new Thread(rel);
    job.run();
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
    final int xreleaseKey = xrel.getNewDatasetKey();
    System.out.println("\n*** XRELEASE " + xreleaseKey + " ***");
    job = new Thread(xrel);
    job.run();

    System.out.println("\n*** COMPARISON ***");
    // compare with expected tree
    assertTree(xreleaseKey, getClass().getResourceAsStream("/txtree/" + project + "/xrelease.txtree"));
  }

  @After
  public void teardown() throws Exception {
    hc.close();
  }
}