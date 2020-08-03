package life.catalogue.db.tree;

import life.catalogue.dao.FileMetricsDatasetDao;

public class DatasetDiffServiceTest extends BaseDiffServiceTest<Integer> {
  static int attemptCnt;

  public DatasetDiffServiceTest() {
    diff = new DatasetDiffService(factory(), new FileMetricsDatasetDao(factory(), treeRepoRule.getRepo()));
  }

  @Override
  Integer provideTestKey() {
    return 1;
  }

}