package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.dao.FileMetricsSectorDao;

public class SectorDiffServiceTest extends BaseDiffServiceTest<DSID<Integer>> {
  static int attemptCnt;

  public SectorDiffServiceTest() {
    diff = new SectorDiffService(factory(), new FileMetricsSectorDao(factory(), treeRepoRule.getRepo()), 10);
  }

  @Override
  DSID<Integer> provideTestKey() {
    return DSID.of(1,1);
  }

}