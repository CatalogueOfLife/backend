package life.catalogue.printer;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.DaoTestBase;
import life.catalogue.dao.FileMetricsDao;
import life.catalogue.junit.TestDataRule;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public abstract class BaseDiffServiceTest<K> extends DaoTestBase {

  BaseDiffService<K> diff;

  public BaseDiffServiceTest() {
    super(TestDataRule.tree());
  }

  @Test
  public void diffBinaryVersion() throws IOException {
    String v = diff.diffBinaryVersion();
    System.out.println(v);
    assertTrue(v.startsWith("diff") || v.startsWith("Apple diff"));
    assertTrue(v.length() > 10);
  }

  @Test
  public void udiff() throws Exception {
    final File f1 = Resources.toFile("trees/coldp.tree.gz");
    final File f2 = Resources.toFile("trees/coldp2.tree.gz");

    BufferedReader br = diff.udiff(provideTestKey(), new int[]{1,2}, 2, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });


    String udiff = IOUtils.toString(br);
    System.out.println(udiff);

    Assert.assertTrue(udiff.startsWith("---"));
  }

  abstract K provideTestKey();

  @Test(expected = FileMetricsDao.AttemptMissingException.class)
  public void missingFile() throws Exception {
    final File bad = new File("/tmp/I do not exist");
    diff.diff(provideTestKey(), new int[]{1,2,3}, i -> {
      switch (i) {
        case 1: return bad;
        case 2: return bad;
      }
      return null;
    });
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

  @Test
  public void namesDiff() throws Exception {
    final File f1 = Resources.toFile("names1.txt");
    final File f2 = Resources.toFile("names2.txt");

    final K key = provideTestKey();
    NamesDiff d = diff.diff(key, new int[]{1,2}, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });

    assertEquals(2, d.getDeleted().size());
    assertEquals(2, d.getInserted().size());
    assertEquals(key, d.getKey());
    assertEquals(1, d.getAttempt1());
    assertEquals(2, d.getAttempt2());
  }
}