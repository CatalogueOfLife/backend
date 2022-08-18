package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
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

public class UsageMatcherTest {

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
    UsageMatcher matcher = new UsageMatcher(dataRule.testData.key, matchingRule.getIndex(), PgSetupRule.getSqlSessionFactory());
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      var orig = num.get(dsid.id("u101"));
      matcher.add(orig);

      var match = matcher.match(num.get(dsid), null);
      ((Synonym)orig).setAccepted(null); // is purposely not populated in matches - parentID is enough
      assertEquals(match.usage, orig);

      matcher.clear();
      match = matcher.match(num.get(dsid), null);
      assertEquals(match.usage, orig);
    }
  }
}