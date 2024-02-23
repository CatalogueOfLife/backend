package life.catalogue.matching;

import life.catalogue.TestDataGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.cache.UsageCache;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.MatchedParentStack;
import life.catalogue.matching.UsageMatch;
import life.catalogue.matching.UsageMatcherGlobal;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static life.catalogue.api.model.SimpleName.sn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class UsageMatcherGlobalTest {

  public final static PgSetupRule pg = new PgSetupRule();
  public final static TestDataRule dataRule = TestDataGenerator.matching();
  public final static NameMatchingRule matchingRule = new NameMatchingRule();
  final int datasetKey = dataRule.testData.key;

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(matchingRule);

  final DSID<String> dsid = DSID.root(datasetKey);
  UsageMatcherGlobal matcher;

  @Before
  public void before() {
    matcher = new UsageMatcherGlobal(NameMatchingRule.getIndex(), UsageCache.hashMap(), SqlSessionFactoryRule.getSqlSessionFactory());
  }

  @Test
  public void match() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var origNU = num.get(dsid.id("oen3"));
      ((Synonym)origNU).setAccepted(null); // is purposely not populated in matches - parentID is enough

      var match = matcher.matchWithParents(datasetKey, num.get(dsid), List.of(), false, false);
      var origSN = new SimpleNameCached(origNU, match.usage.getCanonicalId());
      assertEquals(new SimpleNameCached(match.usage), origSN);

      matcher.clear();
      match = matcher.matchWithParents(datasetKey, num.get(dsid), List.of(), false, false);
      assertEquals(new SimpleNameCached(match.usage), origSN);
    }
  }

  @Test
  public void matchCl() {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var origNU = num.get(dsid.id("oen5"));

      var cl = Classification.newBuilder()
         .genus("Oenanthe")
         .family("Apiaceae")
         .kingdom("Plantae")
         .build();

      var match = matcher.match(datasetKey, num.get(dsid), cl.asSimpleNames(), false, false);
      var origSN = new SimpleNameCached(origNU, match.usage.getCanonicalId());
      assertEquals(new SimpleNameCached(match.usage), origSN);
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