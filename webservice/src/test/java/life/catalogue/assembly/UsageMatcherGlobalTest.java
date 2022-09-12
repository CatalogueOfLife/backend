package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameWithNidx;
import life.catalogue.api.model.Synonym;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class UsageMatcherGlobalTest {

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();
  final TestDataRule dataRule = TestDataRule.fish();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);
  final DSID<String> dsid = DSID.root(dataRule.testData.key);

  @Test
  public void match() {
    UsageMatcherGlobal matcher = new UsageMatcherGlobal(matchingRule.getIndex(), PgSetupRule.getSqlSessionFactory());
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var origNU = num.get(dsid.id("u101"));
      var origSN = matcher.add(origNU);

      var match = matcher.match(dsid.getDatasetKey(), num.get(dsid), null);
      ((Synonym)origNU).setAccepted(null); // is purposely not populated in matches - parentID is enough
      assertEquals(match.usage, origSN);

      matcher.clear();
      match = matcher.match(dsid.getDatasetKey(), num.get(dsid), null);
      assertEquals(match.usage, origSN);
    }
  }
}