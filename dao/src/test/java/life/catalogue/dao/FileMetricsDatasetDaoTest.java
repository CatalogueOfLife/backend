package life.catalogue.dao;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.junit.TestDataRule;

import java.io.BufferedReader;
import java.util.stream.Stream;

import org.apache.ibatis.session.SqlSession;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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

  /**
   * This test never calls updateNames, so it only exercises the read-tolerance path: a missing names file
   * is not an error as long as the dataset_import row records a zero name count. This is the regression
   * from 51065a38a: updateNames lives on the shared FileMetricsDao base and started skipping the file for
   * zero-name dataset imports too, but FileMetricsDatasetDao had no equivalent read tolerance.
   */
  @Test
  public void noFileWrittenWhenNoNames() throws Exception {
    int attempt;
    try (SqlSession session = factory().openSession(true)) {
      DatasetImport di = new DatasetImport();
      di.setDatasetKey(key);
      di.setOrigin(DatasetOrigin.EXTERNAL);
      di.setCreatedBy(TestDataRule.TEST_USER.getKey());
      di.setNameCount(0);
      session.getMapper(DatasetImportMapper.class).create(di);
      attempt = di.getAttempt();
    }

    Assert.assertFalse("an empty names file must not be created", fdao.namesFile(key, attempt).exists());

    // reading it back must not blow up
    Assert.assertEquals(0, fdao.getNames(key, attempt).count());
  }

  /**
   * A missing file with no dataset_import row behind it is still a genuine NotFound - the tolerance
   * must not swallow an unknown attempt.
   */
  @Test(expected = FileMetricsDao.AttemptMissingException.class)
  public void unknownAttemptStill404s() throws Exception {
    fdao.getNames(key, 999).count();
  }
}
