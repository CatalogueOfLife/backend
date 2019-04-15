package org.col.dao;

import java.io.BufferedReader;
import java.util.Iterator;
import java.util.stream.Stream;

import org.col.db.mapper.TestDataRule;
import org.gbif.utils.file.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class NamesTreeDaoTest extends DaoTestBase {
  
  DatasetImportDao dao;
  
  public NamesTreeDaoTest() {
    super(TestDataRule.tree());
    dao = new DatasetImportDao(factory(), treeRepoRule.getRepo());
  }
  
  @Test
  public void roundtripTree() throws Exception {
    BufferedReader expected = FileUtils.getInputStreamReader(FileUtils.classpathStream("trees/tree.tree"), "UTF8");
    
    dao.getTreeDao().updateDatasetTree(11, 1);
  
    Stream<String> lines = dao.getTreeDao().getDatasetTree(11, 1);
    assertEquals(expected.lines(), lines);
  }
  
  @Test
  public void roundtripNames() throws Exception {
    dao.getTreeDao().updateDatasetNames(11, 1);
    
    Stream<String> lines = dao.getTreeDao().getDatasetNames(11, 1);
    // we have only NULL index ids in this test dataset :)
    assertFalse(lines.findFirst().isPresent());
  }
  
  public static <T> void assertEquals(Stream<T> expected, Stream<T> toTest) {
    Iterator<T> iter = expected.iterator();
    toTest.forEach(x -> Assert.assertEquals(iter.next(), x));
    assertFalse(iter.hasNext());
  }
}
