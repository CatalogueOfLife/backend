package life.catalogue.matching;

import life.catalogue.TestDataRules;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.Mock;

import static life.catalogue.api.model.SimpleName.sn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class UsageMatcher2IT {

  public final static PgSetupRule pg = new PgSetupRule();
  public final static TestDataRule dataRule = TestDataRules.matching();
  public final static NameMatchingRule matchingRule = new NameMatchingRule();
  final int datasetKey = dataRule.testData.key;

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(matchingRule);

  final DSID<String> dsid = DSID.root(datasetKey);
  UsageMatcher matcher;
  MatchingUtils utils;
  @Mock
  JobExecutor jobExecutor;

  @Before
  public void before() {
    utils = new MatchingUtils(NameMatchingRule.getIndex());
    var matcherFactory = new UsageMatcherFactory(new MatchingConfig(), NameMatchingRule.getIndex(), SqlSessionFactoryRule.getSqlSessionFactory(), jobExecutor);
    matcher = matcherFactory.memory(datasetKey);
    matcher.load(SqlSessionFactoryRule.getSqlSessionFactory());
  }

  @Test
  public void match() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var orig = num.getSimpleCached(dsid.id("oen3"));
      var match = matcher.parseAndMatch(orig);
      assertEquals(new SimpleNameCached(match.usage), orig);
    }
  }

  @Test
  public void matchCl() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var orig = num.getSimpleCached(dsid.id("oen5"));

      var cl = Classification.newBuilder()
         .genus("Oenanthe")
         .family("Apiaceae")
         .kingdom("Plantae")
         .build();
      var snc = new SimpleNameClassified<SimpleNameCached>(orig);
      snc.setClassification(cl.asSimpleNameCached());
      var match = matcher.match(snc, false, false);
      assertEquals(new SimpleNameCached(match.usage), orig);
    }
  }

  @Test
  public void oenanthe() throws Exception {
    var match = match(Rank.GENUS, "Oenanthe", "L.", null, null);
    assertEquals("oen1", match.usage.getId());
    assertEquals(MatchType.EXACT, match.type);

    match = match(Rank.GENUS, "Oenanthe", "L", null, null);
    assertEquals("oen1", match.usage.getId());
    assertEquals(MatchType.EXACT, match.type); // we allow whitespace and punctuation differences

    match = match(Rank.GENUS, "Oenanthe", "Lin.", null, null);
    assertEquals("oen1", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(Rank.GENUS, "Oenanthe", "Linné", null, null);
    assertEquals("oen1", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(Rank.GENUS, "Oenanthe", "Linus", null, null); // debatable whether this should match
    assertEquals("oen1", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(Rank.GENUS, "Oenanthe", "V.", null, null);
    assertEquals("oen2", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(Rank.GENUS, "Œnanthe", "Vieil.", null, null);
    assertEquals("oen2", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(Rank.GENUS, "Œnanthe", "1816", null, null);
    assertEquals("oen2", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(Rank.GENUS, "Oenanthe", "Vieillot", null, null);
    assertEquals("oen2", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(null, "Oenanthe", "Vieillot", null, null);
    assertEquals("oen2", match.usage.getId());
    assertEquals(MatchType.VARIANT, match.type);

    match = match(Rank.GENUS, "Oenanthe", "Vieillot, 1816", null, null);
    assertEquals("oen2", match.usage.getId());
    assertEquals(MatchType.EXACT, match.type);

    match = match(Rank.GENUS, "Oenanthe", "Vieillot 1816", null, null);
    assertEquals("oen2", match.usage.getId());
    assertEquals(MatchType.EXACT, match.type);

    // entirely different author
    match = match(Rank.GENUS, "Oenanthe", "Döring", null, null);
    assertFalse(match.isMatch());
    assertEquals(MatchType.NONE, match.type);

    match = match(null, "Oenanthe", "Döring", null, null);
    assertFalse(match.isMatch());
    assertEquals(MatchType.NONE, match.type);

    match = match(Rank.GENUS, "Oenanthe", "1918", null, null);
    assertFalse(match.isMatch());
    assertEquals(MatchType.NONE, match.type);

    //TODO: this doesn't work - it matches to the plant as thats the only name without a year and could potentially match
    match = match(null, "Oenanthe", "1918", null, null);
    //assertFalse(match.isMatch());
    //assertEquals(MatchType.NONE, match.type);

    // without author and classification we should get non
    match = match(Rank.GENUS, "Oenanthe", null, null, null);
    assertEquals(MatchType.AMBIGUOUS, match.type);

    // with classification we should be good again
    match = match(Rank.GENUS, "Oenanthe", null, null, null, sn(Rank.CLASS, "Animalia"), sn(Rank.CLASS, "Aves"));
    assertEquals("oen2", match.usage.getId());

    match = match(Rank.GENUS, "Oenanthe", null, null, null, sn(Rank.CLASS, "Plantae"));
    assertEquals("oen1", match.usage.getId());
  }

  @Test
  public void chaetocnema() throws Exception {
    var match = match(Rank.GENUS, "Chaetocnema", "Stephens, 1831", null, null);
    assertEquals("g1", match.usage.getId());

    match = match(Rank.GENUS, "Chaetocnema", "1831", null, null);
    assertEquals("g1", match.usage.getId());

    match = match(Rank.SUBGENUS, "Chaetocnema", "Stephens, 1831", null, null);
    assertEquals("sg2", match.usage.getId());

    match = match(Rank.SUBGENUS, "Chaetocnema (Chaetocnema)", "Stephens, 1831", null, null);
    assertEquals("sg2", match.usage.getId());

    match = match(Rank.SUBGENUS, "Chaetocnema (Chaetocnema)", "Ruan & Yang & Konstantinov & Prathapan & Zhang, 2019", null, null);
    assertEquals("sg3", match.usage.getId());
  }

  UsageMatch match(Rank rank, String name, String authors, TaxonomicStatus status, NomCode code, SimpleName... parents) throws InterruptedException {
    return UsageMatcherIT.match(matcher, utils, rank, name, authors, status, code, parents);
  }
}