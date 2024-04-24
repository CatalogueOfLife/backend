package life.catalogue.dao;

import life.catalogue.common.io.UTF8IoUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.util.stream.Stream;

public class FileMetricsDatasetDaoTest extends FileMetricsDaoTestBase<Integer> {

  FileMetricsDatasetDao fdao;

  @Before
  public void initDao(){
    fdao = new FileMetricsDatasetDao(factory(), treeRepoRule.getRepo());
    dao = fdao;
    key = 11;
  }

  @Test
  public void roundtripTree() throws Exception {
    BufferedReader expected = UTF8IoUtils.readerFromStream(getClass().getResourceAsStream("/trees/tree.tree"));


    fdao.updateTree(key, key, 1);

    Stream<String> lines = fdao.getTree( key, 1);
    assertEquals(expected.lines(), lines);
  }

  @Test
  public void bucket() throws Exception {
    Assert.assertEquals("000", FileMetricsDatasetDao.bucket(0));
    Assert.assertEquals("003", FileMetricsDatasetDao.bucket(3));
    Assert.assertEquals("013", FileMetricsDatasetDao.bucket(13));
    Assert.assertEquals("133", FileMetricsDatasetDao.bucket(133));
    Assert.assertEquals("999", FileMetricsDatasetDao.bucket(999));
    Assert.assertEquals("000", FileMetricsDatasetDao.bucket(1000));
    Assert.assertEquals("333", FileMetricsDatasetDao.bucket(1333));
    Assert.assertEquals("001", FileMetricsDatasetDao.bucket(1789001));
    Assert.assertEquals("456", FileMetricsDatasetDao.bucket(-3456));
  }
}
