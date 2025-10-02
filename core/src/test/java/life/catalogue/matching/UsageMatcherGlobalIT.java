package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.UsageCache;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.*;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static life.catalogue.api.model.SimpleName.sn;
import static org.junit.Assert.*;

/**
 * Parameterized name usage matching against a source in text tree format
 */
public class UsageMatcherGlobalIT {

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

  int datasetKey;
  DSID<String> dsid;
  UsageMatcherGlobal matcher;

  @Before
  public void before() {
    matcher = new UsageMatcherGlobal(NameMatchingRule.getIndex(), UsageCache.hashMap(), SqlSessionFactoryRule.getSqlSessionFactory());
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

  UsageMatch match(Rank rank, String name, String authors, TaxonomicStatus status, NomCode code, SimpleName... parents) throws InterruptedException {
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

    var result = matcher.match(datasetKey, u, List.of(parents), false, true);
    return result;
  }
}