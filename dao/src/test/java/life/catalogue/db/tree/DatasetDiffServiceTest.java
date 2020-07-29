package life.catalogue.db.tree;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.DaoTestBase;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.TestDataRule;
import org.junit.Test;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DatasetDiffServiceTest extends DaoTestBase {
  static int attemptCnt;
  DatasetDiffService diff;
  DatasetImportDao dao;
  
  public DatasetDiffServiceTest() {
    super(TestDataRule.tree());
    dao = new DatasetImportDao(factory(), treeRepoRule.getRepo());
    diff = new DatasetDiffService(factory(), dao.getFileMetricsDao());
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
  public void namesdiff() throws Exception {
    final File f1 = Resources.toFile("names1.txt");
    final File f2 = Resources.toFile("names2.txt");
    
    NamesDiff d = diff.namesDiff(99, new int[]{1,2}, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });
    
    assertEquals(2, d.getDeleted().size());
    assertEquals(2, d.getInserted().size());
    assertEquals(99, d.getKey());
    assertEquals(1, d.getAttempt1());
    assertEquals(2, d.getAttempt2());
  }
}