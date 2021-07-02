package life.catalogue.db.tree;

import life.catalogue.common.io.Resources;
import life.catalogue.dao.FileMetricsDatasetDao;

import java.io.File;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;

public class DatasetDiffServiceTest extends BaseDiffServiceTest<Integer> {
  static int attemptCnt;

  public DatasetDiffServiceTest() {
    diff = new DatasetDiffService(factory(), new FileMetricsDatasetDao(factory(), treeRepoRule.getRepo()));
  }

  @Override
  Integer provideTestKey() {
    return 1;
  }

  @Test
  public void diffItisNames() throws Exception {
    final File f1 = Resources.toFile("trees/itis/36-names.txt.gz");
    final File f2 = Resources.toFile("trees/itis/37-names.txt.gz");

    StopWatch watch = StopWatch.createStarted();
    var br = diff.udiff(provideTestKey(), new int[]{1,2}, 0, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });
    String udiff = IOUtils.toString(br);
    System.out.println(udiff);

    Assert.assertTrue(udiff.startsWith("--- dataset_"));

    watch.stop();
    System.out.println(watch);
  }
}