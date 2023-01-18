package life.catalogue.release;

import life.catalogue.TestDataGenerator;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.SimpleName;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.Rank;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class HomotypicConsolidatorTest {

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final NameMatchingRule matchingRule = new NameMatchingRule();
  final TestDataRule dataRule = TestDataGenerator.homotypigGrouping();
  HomotypicConsolidator consolidator;

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);

  @Before
  public void init() {
    consolidator = new HomotypicConsolidator(PgSetupRule.getSqlSessionFactory(), dataRule.testData.key, null);
  }

  @Test
  public void groupAll() {
    consolidator.groupAll();
  }

  @Test
  public void groupFamily() {
    consolidator.groupFamily(SimpleName.sn("x5", Rank.FAMILY, "Chironomidae", ""));
  }
}