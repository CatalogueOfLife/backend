package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.NameUsageDao;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.junit.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The related fixture holds Betula pubescens Ehrh. accepted in dataset 100 and twice in dataset 101,
 * the way the IUCN Red List does: accepted as t1 and as s1, a synonym of Betula klokovii.
 * s1 comes first by id, so picking t1 must be the matcher's doing.
 * See https://github.com/CatalogueOfLife/backend/issues/1705
 */
public class UsageMatcherMatchRelatedTest {
  final static SqlSessionFactoryRule pg = new PgSetupRule();
  final static TestDataRule dataRule = TestDataRule.related();
  final static NameMatchingRule matchingRule = new NameMatchingRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(dataRule)
    .around(matchingRule);

  @Test
  public void acceptedOverSynonym() {
    // both usages of the name are related, the synonym is a real candidate
    var related = new NameUsageDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru())
      .related(100, "u1", false, null, null, null, List.of(101), null);
    assertEquals(Set.of("s1", "t1"), related.stream().map(SimpleName::getId).collect(Collectors.toSet()));

    var m = UsageMatcher.matchRelated(SqlSessionFactoryRule.getSqlSessionFactory(), DSID.of(100, "u1"), 101);
    assertNotNull(m);
    assertEquals("t1", m.getId());
    assertEquals(TaxonomicStatus.ACCEPTED, m.getStatus());
  }

  @Test
  public void noMatch() {
    // Betula klokovii is missing in dataset 100
    assertNull(UsageMatcher.matchRelated(SqlSessionFactoryRule.getSqlSessionFactory(), DSID.of(101, "k1"), 100));
  }

  @Test
  public void missingUsage() {
    assertThrows(NotFoundException.class,
      () -> UsageMatcher.matchRelated(SqlSessionFactoryRule.getSqlSessionFactory(), DSID.of(100, "nope"), 101));
  }
}
