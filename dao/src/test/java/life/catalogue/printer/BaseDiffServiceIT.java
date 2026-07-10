package life.catalogue.printer;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.DaoTestBase;
import life.catalogue.dao.FileMetricsDao;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public abstract class BaseDiffServiceIT<K> extends DaoTestBase {

  BaseDiffService<K> diff;

  public BaseDiffServiceIT() {
    super(TestDataRule.tree());
  }

  abstract K provideTestKey();

  /**
   * Stages two real, differing gzipped name files at the DAO's stored paths for attempts 1 and 2
   * of {@link #provideTestKey()}, then diffs them through the public {@code diff.diff(key, "1..2")}
   * entry point (no udiff / subprocess involved anymore).
   */
  @Test
  public void namesDiff() throws Exception {
    final File f1 = Resources.toFile("trees/itis/36-names.txt.gz");
    final File f2 = Resources.toFile("trees/itis/37-names.txt.gz");

    final K key = provideTestKey();
    File t1 = diff.dao.namesFile(key, 1);
    File t2 = diff.dao.namesFile(key, 2);
    FileUtils.forceMkdirParent(t1);
    FileUtils.forceMkdirParent(t2);
    FileUtils.copyFile(f1, t1);
    FileUtils.copyFile(f2, t2);

    NamesDiff d = diff.diff(key, "1..2");
    assertNotNull(d);
    assertEquals("dataset_" + key + "#1", d.getLabel1());
    assertEquals("dataset_" + key + "#2", d.getLabel2());
    assertFalse(d.isIdentical());
  }

  @Test(expected = FileMetricsDao.AttemptMissingException.class)
  public void missingFile() throws Exception {
    diff.diff(provideTestKey(), "1..2");
  }

  @Test
  public void attemptParsing() throws Exception {
    assertArrayEquals(new int[]{1,2}, diff.parseAttempts("1..2", ()-> Collections.EMPTY_LIST));
    assertArrayEquals(new int[]{10,120}, diff.parseAttempts("10..120", ()-> Collections.EMPTY_LIST));
  }

  @Test(expected = NotFoundException.class)
  public void attemptParsingFail() throws Exception {
    diff.parseAttempts("", ()-> Collections.EMPTY_LIST);
  }

  @Test(expected = IllegalArgumentException.class)
  public void attemptParsingFailBad() throws Exception {
    diff.parseAttempts("1234", ()-> Collections.EMPTY_LIST);
  }

  @Test(expected = IllegalArgumentException.class)
  public void attemptParsingFailBadSequence() throws Exception {
    diff.parseAttempts("5..3", ()-> Collections.EMPTY_LIST);
  }
}
