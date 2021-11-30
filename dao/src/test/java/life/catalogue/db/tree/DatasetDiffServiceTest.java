package life.catalogue.db.tree;

import life.catalogue.common.io.Resources;
import life.catalogue.dao.FileMetricsDatasetDao;

import java.awt.*;
import java.io.File;
import java.util.List;

import life.catalogue.db.TestDataRule;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.Assert;
import org.junit.Test;

public class DatasetDiffServiceTest extends BaseDiffServiceTest<Integer> {
  static int attemptCnt;
  final DatasetDiffService diffService;

  public DatasetDiffServiceTest() {
    diffService = new DatasetDiffService(factory(), new FileMetricsDatasetDao(factory(), treeRepoRule.getRepo()), 10);
    diff = diffService;
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

  @Test
  public void diffItisNamesWithOther() throws Exception {
    var br = diffService.datasetNamesDiff(1, TestDataRule.TREE.key, null, 3, null, null, true);
    String udiff = IOUtils.toString(br);
    System.out.println(udiff);

    Assert.assertTrue(udiff.startsWith("--- dataset_"));

    // with root
    br = diffService.datasetNamesDiff(1, TestDataRule.TREE.key, List.of("t10", "t30"), 3, null, null, true);
    udiff = IOUtils.toString(br);
    System.out.println(udiff);

    Assert.assertTrue(udiff.startsWith("--- dataset_"));
  }
}