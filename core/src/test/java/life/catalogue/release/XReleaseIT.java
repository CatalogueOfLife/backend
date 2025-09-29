package life.catalogue.release;

import life.catalogue.TestConfigs;
import life.catalogue.TestUtils;
import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSyncMergeIT;
import life.catalogue.assembly.SectorSyncTestBase;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.dao.*;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.img.ThumborConfig;
import life.catalogue.img.ThumborService;
import life.catalogue.junit.*;
import life.catalogue.matching.MatchingConfig;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NameIndexImpl;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.Assert.*;
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

  final NameUsageArchiver archiver = new NameUsageArchiver(SqlSessionFactoryRule.getSqlSessionFactory());
  CloseableHttpClient hc;
  ProjectCopyFactory projectCopyFactory;
  @Mock
  JobExecutor jobExecutor;

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
      {"stable-ids", List.of(
        "src1",
        "src2"
      )},
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
        "nomen",
        "bdj"
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
    System.out.println("\n***");
    System.out.println("*** STARTING TEST " + project + " ***");
    System.out.println("***\n");
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
    var tdao = new TaxonDao(SqlSessionFactoryRule.getSqlSessionFactory(), nDao, null, new ThumborService(new ThumborConfig()), NameUsageIndexService.passThru(), null, validator);
    var sdao = new SectorDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), tdao, validator);
    tdao.setSectorDao(sdao);
    var broker = TestUtils.mockedBroker();
    var matcherFactory = new UsageMatcherFactory(new MatchingConfig(), NameMatchingRule.getIndex(), SqlSessionFactoryRule.getSqlSessionFactory(), jobExecutor);
    var syncFactory = new SyncFactory(SqlSessionFactoryRule.getSqlSessionFactory(), matcherFactory, NameMatchingRule.getIndex(), sdao, siDao, eDao,
      NameUsageIndexService.passThru(), broker
    );
    var cfg = TestConfigs.build();

    hc = HttpClientBuilder.create().build();
    var du = new DownloadUtil(hc);
    var dDao = new DatasetDao(factory, du, diDao, validator, broker);
    var rDao = mock(ReferenceDao.class);
    var exportManager = mock(ExportManager.class);
    var doiService = mock(DoiService.class);
    var doiUpdater = mock(DoiUpdater.class);
    projectCopyFactory = new ProjectCopyFactory(hc, NameMatchingRule.getIndex(), syncFactory, matcherFactory, diDao, dDao, siDao, rDao, nDao, sdao,
      exportManager, NameUsageIndexService.passThru(), ImageService.passThru(), doiService, doiUpdater,
      SqlSessionFactoryRule.getSqlSessionFactory(), validator,
      cfg.release, cfg.doi, cfg.apiURI, cfg.clbURI
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
    // archive release for ids
    archiver.archiveRelease(releaseKey, true);

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

    // make sure we always have no temp ids
    AtomicInteger count = new AtomicInteger();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processIds(xreleaseKey, true, ShortUUID.MIN_LEN), u -> {
        var nu = num.get(DSID.of(xreleaseKey, u));
        System.out.println(nu + " -> " + nu.getName().getType());
        if (NameIndexImpl.INDEX_NAME_TYPES.contains(nu.getName().getType())) {
          count.incrementAndGet();
        }
      });
    }
    assertEquals(0, count.get());

    conditionalChecks(xrel);
  }

  private void conditionalChecks(XRelease xrel) {
    if (project.equals("inverse_ranks")) {
      // wrong rank order issue
      final DSID<String> key = DSID.root(xrel.newDatasetKey);
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
        var num = session.getMapper(NameUsageMapper.class);
        var vm = session.getMapper(VerbatimSourceMapper.class);
        var nu = num.findOne(xrel.newDatasetKey, Rank.FAMILY, "Anguidae");
        assertNotNull(nu);
        var v = vm.getByUsage(key.id(nu.getId()));
        assertTrue(v.getIssues().contains(Issue.CLASSIFICATION_RANK_ORDER_INVALID));
      }
    }
  }

  @After
  public void teardown() throws Exception {
    hc.close();
  }
}