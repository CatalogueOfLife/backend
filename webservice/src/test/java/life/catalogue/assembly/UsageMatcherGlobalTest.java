package life.catalogue.assembly;

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

      var match = matcher.matchWithParents(datasetKey, num.get(dsid), List.of());
      var origSN = new SimpleNameCached(origNU, match.usage.getCanonicalId());
      assertEquals(new SimpleNameCached(match.usage), origSN);

      matcher.clear();
      match = matcher.matchWithParents(datasetKey, num.get(dsid), List.of());
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

      var match = matcher.match(datasetKey, num.get(dsid), cl);
      var origSN = new SimpleNameCached(origNU, match.usage.getCanonicalId());
      assertEquals(new SimpleNameCached(match.usage), origSN);
    }
  }

  @Test
  public void oenanthe() throws Exception {
    var match = match(Rank.GENUS, "Oenanthe", "L.", null, null);
    assertEquals("oen1", match.usage.getId());

    match = match(Rank.GENUS, "Oenanthe", "V.", null, null);
    assertEquals("oen2", match.usage.getId());

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
    n.setRank(ObjectUtils.coalesce(rank, opt.get().getName().getRank(), Rank.UNRANKED));
    n.setScientificName(name);
    n.setAuthorship(authors);
    n.setCode(code);

    status = ObjectUtils.coalesce(status, TaxonomicStatus.ACCEPTED);
    NameUsageBase u = status.isSynonym() ? new Synonym() : new Taxon();
    u.setName(n);
    u.setDatasetKey(Datasets.COL);
    u.setStatus(status);
    u.setNamePhrase(opt.get().getTaxonomicNote());

    var matchedParents = Arrays.stream(parents)
                              .map(this::fromRankedName)
                              .collect(Collectors.toList());
    var result = matcher.matchWithParents(datasetKey, u, matchedParents);
    return result;
  }

  MatchedParentStack.MatchedUsage fromRankedName(SimpleName sn) {
    SimpleNameWithNidx sn2 = new SimpleNameWithNidx(sn);
    MatchedParentStack.MatchedUsage mu = new MatchedParentStack.MatchedUsage(sn2);
    return mu;
  }
}