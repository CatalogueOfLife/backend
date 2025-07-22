package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.dao.FileMetricsSectorDao;

public class SectorDiffServiceIT extends BaseDiffServiceIT<DSID<Integer>> {
  static int attemptCnt;

  public SectorDiffServiceIT() {
    diff = new SectorDiffService(factory(), new FileMetricsSectorDao(factory(), treeRepoRule.getRepo()), 10);
  }

  @Override
  DSID<Integer> provideTestKey() {
    return DSID.of(1,1);
  }

}