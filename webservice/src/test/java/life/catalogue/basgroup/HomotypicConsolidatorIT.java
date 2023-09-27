package life.catalogue.basgroup;

import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.tree.TxtTreeDataRule;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

/**
 * Many consolidation tests in one text tree file.
 * See homconsolidation.md markdown file for expectations.
 */
@Ignore("work in progress")
public class HomotypicConsolidatorIT {
  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final int datasetKey = 100;
  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(datasetKey, "txtree/homconsolidation.txtree"))
    .around(matchingRule);

  @Test
  public void ausBus() throws IOException {
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey, LinneanNameUsage::getSectorKey);
    hc.consolidate();
    assertNoLoop(datasetKey);
    SectorSyncIT.assertTree(datasetKey, null, getClass().getResourceAsStream("/txtree/homconsolidation-expected.txtree"));
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