package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.junit.*;

import java.util.Set;
import java.util.stream.Collectors;

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
    try (var store = new UsageMatcherPgStore(102, factory)) {
      var u1 = store.get("u1x");
      assertEquals("u1x", u1.getId());

      var u2 = store.get("u2x");
      assertEquals("u2x", u2.getId());

      var cl = store.getClassification("u2x");
      assertEquals(1, cl.size());

      // single-tier canonical grouping: u1x ("Abies alba Miller", EXACT) and u2x ("Abies alba Mill.",
      // VARIANT) both resolve to the very same canonical names index entry (id 2 = "Abies alba",
      // self-referencing), so the canonical group for dataset 102 contains both usages - not just the
      // one that happened to be looked up first.
      var usages = store.usagesByCanonicalId(u1.getCanonicalId());
      assertEquals(2, usages.size());
      var ids = usages.stream().map(SimpleNameClassified::getId).collect(Collectors.toSet());
      assertEquals(Set.of(u1.getId(), u2.getId()), ids);
    }
  }
}