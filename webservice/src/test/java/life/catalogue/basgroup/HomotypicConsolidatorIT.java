package life.catalogue.basgroup;

import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.TxtTreeDataRule;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

/**
 * Many consolidation tests in one text tree file.
 * See homconsolidation.md markdown file for expectations.
 */
public class HomotypicConsolidatorIT {
  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final int datasetKey = 100;
  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(datasetKey, "txtree/homconsolidation.txtree")) // loads prio values into sector keys
    .around(matchingRule);

  @Test
  public void homconsolidation() throws IOException {
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? Integer.MAX_VALUE : lnu.getSectorKey()
    );
    hc.consolidate();
    assertNoLoop(datasetKey);
    SectorSyncIT.assertTree("homconsolidation-expected.txtree", datasetKey, null, getClass().getResourceAsStream("/txtree/homconsolidation-expected.txtree"));
  }

  public static void assertNoLoop(int datasetKey) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var cycles = session.getMapper(NameUsageMapper.class).detectLoop(datasetKey);
      if (cycles != null && !cycles.isEmpty()) {
        cycles.forEach(id -> System.out.println(id));
        throw new IllegalStateException("Loops in classification at id=" + cycles.get(0));
      }
    }
  }
}