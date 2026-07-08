package life.catalogue.matching;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.MatchingConfig;
import life.catalogue.dao.UserDao;
import life.catalogue.junit.*;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.mockito.Mock;

import com.codahale.metrics.MetricRegistry;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class UsageMatcherIT {

  final static SqlSessionFactoryRule pg = new PgSetupRule(); //PgConnectionRule("col", "postgres", "postgres");
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static TestDataRule dataRule = TestDataRule.empty();
  final static NameMatchingRule matchingRule = new NameMatchingRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(treeRepoRule)
    .around(dataRule)
    .around(matchingRule);

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  int datasetKey;
  DSID<String> dsid;
  UsageMatcher matcher;
  MatchingUtils utils;
  @Mock
  JobExecutor jobExecutor;

  @Before
  public void before() {
    utils = new MatchingUtils(NameMatchingRule.getIndex());
  }

  void loadDataset(int key) {
    String resource = "matching/"+key + ".txtree";
    TxtTreeDataRule.TreeDataset rule = new TxtTreeDataRule.TreeDataset(key, resource, "Dataset "+key, DatasetOrigin.EXTERNAL);
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(List.of(rule))) {
      treeRule.before();
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
    matchingRule.rematch(key);
    datasetKey = key;
    dsid = DSID.root(key);
    var factory = new UsageMatcherFactory(new MatchingConfig(), NameMatchingRule.getIndex(), SqlSessionFactoryRule.getSqlSessionFactory(), jobExecutor);
    matcher = factory.memory(datasetKey);
    matcher.load(SqlSessionFactoryRule.getSqlSessionFactory());
    System.out.println("\n***** Loaded dataset " + key + " *****\n");
  }

  static Classification.ClassificationBuilder cl(){
    return Classification.newBuilder();
  }

  @Test
  public void oenantheTubella() throws InterruptedException {
    loadDataset(1);

    // Oenanthe cross code homonym
    var m = match(Rank.GENUS, "Oenanthe", null, cl().family("Apiaceae").kingdom("Plantae"));
    assertMatch(m, "Oen1");

    m = match(Rank.GENUS, "Oenanthe", "L.", cl());
    assertMatch(m, "Oen1");

    m = match(Rank.GENUS, "Oenanthe", "Vieillot", cl());
    assertMatch(m, "Oen2");

    m = match(Rank.GENUS, "Oenanthe",null, cl().class_("Aves"));
    assertMatch(m, "Oen2");

    // Tubella genus
    m = match(Rank.GENUS, "Tubella",null, cl().subkingdom("Protista"));
    assertMatch(m, "TubPr");
    m = match(Rank.GENUS, "Tubella","Odin", cl());
    assertMatch(m, "TubPr");

    m = match(Rank.GENUS, "Tubella",null, cl().family("Orchidaceae"));
    assertMatch(m, "TubOrch");
    m = match(Rank.GENUS, "Tubella",null, cl().class_("Monocot"));
    assertMatch(m, "TubOrch");

    m = match(Rank.GENUS, "Tubella",null, cl().phylum("Porifera"));
    assertMatch(m, "TubPor2");
    m = match(Rank.GENUS, "Tubella",null, cl().phylum("Porifera").family("Spongillidae"));
    assertMatch(m, "TubPor1");
  }

  @Test
  public void metopiinae() throws InterruptedException {
    loadDataset(2);

    var m = match(Rank.SUBFAMILY, "Metopiinae", null, cl());
    assertMatch(m, "SYN");

    m = match(Rank.SUBFAMILY, "Metopiinae", "Förster", cl());
    assertMatch(m, "SYN");

    // Metopiinae is in reality a Hymenoptera wasp - so the tax group analysis gets confused here
    m = match(Rank.SUBFAMILY, "Metopiinae", "Förster", cl().kingdom("Animalia").phylum("Arthropoda").class_("Insecta").order("Hymenoptera").family("Ichneumonidae"));
    assertNoMatch(m);
  }

  /**
   *  https://github.com/CatalogueOfLife/backend/issues/1441
    */
  @Test
  public void unranked() throws InterruptedException {
    loadDataset(3);

    var m = match(Rank.GENUS, "Ichneumon", null, cl());
    assertMatch(m, "Ichneumon");
    m = match(null, "Ichneumon", null, cl());
    assertMatch(m, "Ichneumon");
    m = match(Rank.GENUS, "Ichneumon", "Linnaeus", cl());
    assertMatch(m, "Ichneumon");
    m = match(null, "Ichneumon", "Linnaeus", cl());
    assertMatch(m, "Ichneumon");
    m = match(null, "Ichneumon", "1758", cl());
    assertMatch(m, "Ichneumon");

    m = match(Rank.GENUS, "Carria", "Blanchard 1850 (Oct.)", cl());
    assertMatch(m, "Carria");
    m = match(Rank.GENUS, "Carria", "Blanchard 1850", cl());
    assertMatch(m, "Carria");
    m = match(Rank.GENUS, "Carria", "Blanchard", cl());
    assertMatch(m, "Carria");
    m = match(null, "Carria", "Blanchard", cl());
    assertMatch(m, "Carria");
    m = match(null, "Carria", null, cl());
    assertMatch(m, "Carria");

    m = match(Rank.SPECIES, "Ichneumon fuscatus", null, cl());
    assertMatch(m, "Ichneumon_fuscatus");
    m = match(Rank.SPECIES, "Ichneumon fuscatta", null, cl());
    assertMatch(m, "Ichneumon_fuscatus");
  }

  /**
   * Higher rank fallback: when the name itself cannot be matched to a usage,
   * the matcher walks up the explicit classification and the genus/species derived
   * from a bi/trinomial name and returns the closest higher rank usage.
   */
  @Test
  public void higherRank() throws InterruptedException {
    loadDataset(4);

    // genus derived from an unmatched binomial
    var m = matchHigher(Rank.SPECIES, "Bembidion fakeum", null, cl());
    assertMatch(m, "Bembidion");
    assertEquals(MatchType.HIGHERRANK, m.type);

    // species derived from an unmatched infraspecific name is preferred over the genus
    m = matchHigher(Rank.SUBSPECIES, "Bembidion lampros fakevar", null, cl());
    assertMatch(m, "Bembidion_lampros");
    assertEquals(MatchType.HIGHERRANK, m.type);

    // uninomial that does not match walks up the explicit classification
    m = matchHigher(Rank.GENUS, "Faketus", null, cl().family("Carabidae").order("Coleoptera"));
    assertMatch(m, "Carabidae");
    assertEquals(MatchType.HIGHERRANK, m.type);

    // an ambiguous derived genus (cross family homonym) without disambiguating classification is no match
    m = matchHigher(Rank.SPECIES, "Agabus fakeus", null, cl());
    assertNoMatch(m);

    // without the higher rank flag the very same query does not match at all
    m = match(Rank.SPECIES, "Bembidion fakeum", null, cl());
    assertNoMatch(m);
  }

  /**
   * Full self-maintaining cycle on a real DB: publishing an in-scope EXTERNAL dataset above threshold
   * schedules an async build that lands a persistent matcher on disk; deleting it removes the matcher again.
   */
  @Test
  public void publishBuildRemoveCycle() throws Exception {
    // load a fresh EXTERNAL dataset (key 5) from the dataset-1 tree so we don't collide with other tests
    int key = 5;
    TxtTreeDataRule.TreeDataset rule = new TxtTreeDataRule.TreeDataset(key, "matching/1.txtree", "Dataset " + key, DatasetOrigin.EXTERNAL);
    try (TxtTreeDataRule treeRule = new TxtTreeDataRule(List.of(rule))) {
      treeRule.before();
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
    matchingRule.rematch(key);
    datasetKey = key;

    MatchingConfig cfg = new MatchingConfig();
    cfg.storageDir = tmp.getRoot();
    cfg.pgMatcherThreshold = 1; // dataset 1 has more than 1 usage → persistent, not "small"

    UserDao uDao = mock(UserDao.class);
    User user = new User();
    user.setKey(Users.TESTER);
    user.setUsername("tester");
    doReturn(user).when(uDao).get(any());
    JobExecutor exec = new JobExecutor(new JobConfig(), new MetricRegistry(), null, uDao);
    try {
      var f = new UsageMatcherFactory(cfg, NameMatchingRule.getIndex(), SqlSessionFactoryRule.getSqlSessionFactory(), exec);

      // fire a publish event: was private, now public
      Dataset old = new Dataset();
      old.setKey(datasetKey);
      old.setOrigin(DatasetOrigin.EXTERNAL);
      old.setPrivat(true);
      Dataset now = new Dataset();
      now.setKey(datasetKey);
      now.setOrigin(DatasetOrigin.EXTERNAL);
      now.setPrivat(false);
      f.datasetChanged(DatasetChanged.changed(now, old, Users.TESTER));

      // wait for the async build job to drain
      drain(exec);
      assertNotNull("matcher should exist after publish build", f.get(datasetKey));
      assertTrue("persistent store dir should exist", cfg.dir(datasetKey).isDirectory());

      // now delete the dataset → matcher must be dropped
      f.datasetChanged(DatasetChanged.deleted(now, Users.TESTER));
      assertNull("matcher should be removed after delete", f.get(datasetKey));
      assertFalse("persistent store dir should be gone", cfg.dir(datasetKey).isDirectory());
    } finally {
      exec.stop();
    }
  }

  /**
   * Monomial homonyms & suprageneric_rank filter
   */
  @Test
  public void bacteria() throws InterruptedException {
    loadDataset(5);

    // no match for a homonym
    var m = match(null, "Bacteria", null, cl());
    assertMatch(m, "BacG");

    // match with domain rank
    m = match(Rank.DOMAIN, "Bacteria", null, cl());
    assertMatch(m, "Bac");

    // match with any higher rank
    m = match(Rank.SUPRAGENERIC_NAME, "Bacteria", null, cl());
    assertMatch(m, "Bac");
  }

  private static void drain(JobExecutor exec) throws InterruptedException {
    for (int i = 0; i < 600 && !exec.isIdle(); i++) {
      TimeUnit.MILLISECONDS.sleep(100);
    }
  }

  void assertMatch(UsageMatch m, String id) {
    assertTrue(m.isMatch());
    assertEquals(id, m.usage.getId());
  }
  void assertNoMatch(UsageMatch m) {
    assertFalse(m.isMatch());
  }

  UsageMatch match(Rank rank, String name, String authors, Classification.ClassificationBuilder parents) throws InterruptedException {
    return match(rank, name, authors, null, null, parents.build().asSimpleNames().toArray(new SimpleName[0]));
  }

  UsageMatch matchHigher(Rank rank, String name, String authors, Classification.ClassificationBuilder parents) throws InterruptedException {
    var opt = NameParser.PARSER.parse(name, authors, rank, null, VerbatimRecord.VOID);
    Name n = opt.get().getName();
    n.setDatasetKey(Datasets.COL);
    n.setRankAllowNull(rank);
    n.setScientificName(name);
    n.setAuthorship(authors);

    NameUsageBase u = new Taxon();
    u.setName(n);
    u.setDatasetKey(Datasets.COL);
    u.setStatus(TaxonomicStatus.ACCEPTED);

    var classification = MatchingUtils.toSimpleNameCached(parents.build().asSimpleNames().toArray(new SimpleName[0]));
    var snc = utils.toSimpleNameClassified(u, classification);
    return matcher.match(snc, false, true, true);
  }

  UsageMatch match(Rank rank, String name, String authors, TaxonomicStatus status, NomCode code, SimpleName... parents) throws InterruptedException {
    return match(matcher, utils, rank, name, authors, status, code, parents);
  }

  static UsageMatch match(UsageMatcher matcher, MatchingUtils utils, Rank rank, String name, String authors, TaxonomicStatus status, NomCode code, SimpleName... parents) throws InterruptedException {
    var opt = NameParser.PARSER.parse(name, authors, rank, code, VerbatimRecord.VOID);
    Name n = opt.get().getName();
    n.setDatasetKey(Datasets.COL);
    n.setRankAllowNull(rank); // overwrite name parser rank
    n.setScientificName(name);
    n.setAuthorship(authors);
    n.setCode(code);

    status = ObjectUtils.coalesce(status, TaxonomicStatus.ACCEPTED);
    NameUsageBase u = status.isSynonym() ? new Synonym() : new Taxon();
    u.setName(n);
    u.setDatasetKey(Datasets.COL);
    u.setStatus(status);
    u.setNamePhrase(opt.get().getTaxonomicNote());

    var snc = utils.toSimpleNameClassified(u, MatchingUtils.toSimpleNameCached(parents));
    var result = matcher.match(snc, false, true);
    return result;
  }

}