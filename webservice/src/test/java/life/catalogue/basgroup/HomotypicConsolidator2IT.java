package life.catalogue.basgroup;

import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.tree.TxtTreeDataRule;

import java.io.IOException;
import java.io.InputStream;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

public class HomotypicConsolidator2IT {
  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(100, "txtree/ausbus.txtree"))
    .around(matchingRule);

  @Test
  public void ausBus() throws IOException {
    final int dk = 100;
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), dk, LinneanNameUsage::getSectorKey);
    hc.consolidate();
    assertTree(dk,"t4.txtree", null);
  }

  void assertTree(int datasetKey, String filename, String rootID) throws IOException {
    SectorSyncIT.assertTree(datasetKey, rootID, openResourceStream(filename));
  }

  InputStream openResourceStream(String filename) {
    return getClass().getResourceAsStream("/grouping-trees/" + filename);
  }
}