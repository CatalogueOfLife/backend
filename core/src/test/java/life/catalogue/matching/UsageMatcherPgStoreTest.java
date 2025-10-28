package life.catalogue.matching;

import life.catalogue.junit.*;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.jupiter.api.Assertions.*;

public class UsageMatcherPgStoreTest {
  final static SqlSessionFactoryRule pg = new PgSetupRule(); //PgConnectionRule("col", "postgres", "postgres");
  final static TestDataRule dataRule = TestDataRule.nidx();
  final static NameMatchingRule matchingRule = new NameMatchingRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(matchingRule);

  @Test
  public void basics() {
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    try (var store = new UsageMatcherPgStore(102, factory.openSession(), true)) {
      var u1 = store.get("u1x");
      assertEquals("u1x", u1.getId());

      var u2 = store.get("u2x");
      assertEquals("u2x", u2.getId());

      var cl = store.getClassification("u2x");
      assertEquals(1, cl.size());

      var usages = store.usagesByCanonicalId(u1.getCanonicalId());
      assertEquals(1, usages.size());
      assertEquals(u1.getId(), usages.get(0).getId());
    }
  }
}