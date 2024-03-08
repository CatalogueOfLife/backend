package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.*;
import life.catalogue.assembly.SectorSyncTestBase;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.dao.*;
import life.catalogue.db.*;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.printer.TxtTreeDataRule;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.UsageMatcherGlobal;

import org.gbif.nameparser.api.Rank;

import java.util.*;

import javax.validation.Validation;
import javax.validation.Validator;

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

import com.google.common.eventbus.EventBus;

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
  List<Src> sources;
  List<Sector> sectors = new ArrayList<>();
  XReleaseConfig xCfg;

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    final var biotaSedis = SimpleNameClassified.snc(null, Rank.KINGDOM, null, TaxonomicStatus.PROVISIONALLY_ACCEPTED, "incertae sedis", null);
    biotaSedis.setClassification(List.of(SimpleName.sn("Biota")));

    return Arrays.asList(new Object[][] {
      {"inverse_ranks", cfg(biotaSedis), List.of(
        tax("repdb")
      )},
      {"abronia", cfg(biotaSedis), List.of(
        tax("itis"),
        tax("wcvp"),
        tax("repdb")
      )},
      {"unplaced_synonyms", cfg(biotaSedis), List.of(
        tax("wcvp")
      )},
      {"incertae", cfg(biotaSedis, Set.of("Dictymia serbia", "Dictymia braunii", "Dictymia brownii Döring")), List.of(
        tax("sedis"),
        tax("src2"),
        nom("nomen", Rank.ORDER,Rank.FAMILY,Rank.GENUS,Rank.SPECIES)
      )},
      {"homonyms", cfg(), List.of(
        tax("worms"),
        tax("itis"),
        tax("wcvp"),
        tax("taxref"),
        tax("ala"),
        nom("ipni"),
        tax("irmng")
      )}
    });
  }

  public XReleaseIT(String project, XReleaseConfig xCfg, List<Src> sources) {
    this.project = project.toLowerCase();
    this.sources = sources;
    this.xCfg = xCfg;
  }

  static Src tax(String name) {
    return new Src(name, DatasetType.TAXONOMIC);
  }
  static Src tax(String name, Rank... rank) {
    return new Src(name, DatasetType.TAXONOMIC, rank);
  }
  static Src nom(String name) {
    return new Src(name, DatasetType.NOMENCLATURAL);
  }
  static Src nom(String name, Rank... rank) {
    return new Src(name, DatasetType.NOMENCLATURAL, rank);
  }

  static class Src {
    public final String name;
    public final DatasetType type;
    public final Set<Rank> ranks;

    Src(String name, DatasetType type, Rank... rank) {
      this.name = name;
      this.type = type;
      this.ranks = Set.of(rank);
    }
  }

  static XReleaseConfig cfg() {
    return new XReleaseConfig();
  }
  static XReleaseConfig cfg(SimpleNameClassified<SimpleName> incertaeSedis) {
    var cfg = new XReleaseConfig();
    cfg.incertaeSedis = incertaeSedis;
    return cfg;
  }
  static XReleaseConfig cfg(SimpleNameClassified<SimpleName> incertaeSedis, Set<String> blockedNames) {
    var cfg = new XReleaseConfig();
    cfg.incertaeSedis = incertaeSedis;
    cfg.blockedNames = blockedNames;
    return cfg;
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
    LOG.info("Project {}. Trees: {}", project, sources);
    testNum++;
    // load text trees & create sectors
    List<TxtTreeDataRule.TreeDataset> data = new ArrayList<>();
    data.add(
      new TxtTreeDataRule.TreeDataset(Datasets.COL, "txtree/"+project + "/project.txtree", "COL Checklist", DatasetOrigin.PROJECT)
    );

    int dkey = 100;
    for (Src src : sources) {
      data.add(
        new TxtTreeDataRule.TreeDataset(dkey, "txtree/"+project + "/" + src.name.toLowerCase()+".txtree", src.name, DatasetOrigin.EXTERNAL, src.type)
      );
      Sector s = new Sector();
      s.setDatasetKey(Datasets.COL);
      s.setSubjectDatasetKey(dkey);
      s.setMode(Sector.Mode.MERGE);
      s.setPriority(dkey-99);
      s.setNote(src.name);
      if (src.ranks != null) {
        s.setRanks(src.ranks);
      }
      sectors.add(s);
      dkey++;
    }

    // load source datasets
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(data)) {
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
    xrel.setCfg(xCfg);
    final int xreleaseKey = xrel.getNewDatasetKey();
    System.out.println("\n*** XRELEASE " + xreleaseKey + " ***");
    job = new Thread(xrel);
    job.run();


    var u = getByName(xreleaseKey, Rank.GENUS, "Dendropolyporus");
    assertHasVerbatimSource(u, "5051");

    System.out.println("\n*** COMPARISON ***");
    // compare with expected tree
    assertTree(xreleaseKey, getClass().getResourceAsStream("/txtree/" + project + "/xrelease.txtree"));

    conditionalChecks(project, xrel);
  }

  private void conditionalChecks(String project, XRelease xrel) {
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