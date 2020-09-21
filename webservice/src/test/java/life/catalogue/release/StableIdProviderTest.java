package life.catalogue.release;

import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class StableIdProviderTest {
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  StableIdProvider provider;
  NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(TestDataRule.project())
    .around(matchingRule);


  @Before
  public void init() {
    provider = new StableIdProvider(TestDataRule.TestData.PROJECT.key,3, PgSetupRule.getSqlSessionFactory());
  }

  @Test
  public void run() throws Exception {
    provider.run();
  }

}