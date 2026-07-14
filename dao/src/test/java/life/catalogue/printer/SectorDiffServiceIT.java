package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.config.DiffConfig;
import life.catalogue.dao.FileMetricsSectorDao;

public class SectorDiffServiceIT extends BaseDiffServiceIT<DSID<Integer>> {
  static int attemptCnt;

  public SectorDiffServiceIT() {
    DiffConfig diffCfg = new DiffConfig();
    diffCfg.maxItems = 0; // unlimited so assertions see the full diff
    diff = new SectorDiffService(factory(), new FileMetricsSectorDao(factory(), treeRepoRule.getRepo()), diffCfg);
  }

  @Override
  DSID<Integer> provideTestKey() {
    return DSID.of(1,1);
  }

}