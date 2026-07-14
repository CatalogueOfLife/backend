package life.catalogue.printer;

import life.catalogue.config.DiffConfig;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class DatasetDiffServiceIT extends BaseDiffServiceIT<Integer> {
  static int attemptCnt;
  final DatasetDiffService diffService;

  public DatasetDiffServiceIT() {
    DiffConfig diffCfg = new DiffConfig();
    diffCfg.maxItems = 0; // unlimited so assertions see the full diff
    diffService = new DatasetDiffService(factory(), new FileMetricsDatasetDao(factory(), treeRepoRule.getRepo()), diffCfg);
    diff = diffService;
  }

  @Override
  Integer provideTestKey() {
    return 1;
  }

  /**
   * Exercises the SQL generator's root/parent/rankFilter branches end-to-end, diffing the current
   * (live) names of two real datasets rather than stored attempt snapshots.
   */
  @Test
  public void diffItisNamesWithOther() throws Exception {
    var d = diffService.datasetNamesDiff(1, TestDataRule.TREE.key, null, 3, null, null, true, true, false, null, null);
    assertDiffExists(d);

    // with root
    d = diffService.datasetNamesDiff(1, TestDataRule.TREE.key, List.of("t10", "t30"), 3, null, null, true, true, false, null, null);
    assertDiffExists(d);

    // with parents & no rank
    d = diffService.datasetNamesDiff(1, TestDataRule.TREE.key, List.of("t10", "t30"), 3, null, null, true, true, true, null, null);
    assertDiffExists(d);

    // with parents & rank given
    d = diffService.datasetNamesDiff(1, TestDataRule.TREE.key, null, 3, null, null, true, true, true, Rank.FAMILY, null);
    assertDiffExists(d);
  }

  private void assertDiffExists(NamesDiff d) {
    Assert.assertNotNull(d);
    Assert.assertFalse(d.isIdentical());
  }
}
