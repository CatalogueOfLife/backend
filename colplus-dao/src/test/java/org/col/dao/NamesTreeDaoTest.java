package org.col.dao;

import java.io.BufferedReader;
import java.util.Iterator;
import java.util.stream.Stream;

import org.col.db.mapper.InitMybatisRule;
import org.gbif.utils.file.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class NamesTreeDaoTest extends DaoTestBase {
  
  DatasetImportDao dao;
  
  public NamesTreeDaoTest() {
    super(InitMybatisRule.tree());
    dao = new DatasetImportDao(factory());
  }
  
  @Test
  public void roundtripTree() throws Exception {
    BufferedReader expected = FileUtils.getInputStreamReader(FileUtils.classpathStream("trees/tree.tree"), "UTF8");
    
    dao.getTreeDao().updateDatasetTree(11, 1);
  
    Stream<String> lines = dao.getTreeDao().getDatasetTree(11, 1);
    assertEquals(expected.lines(), lines);
  }
  
  public static <T> void assertEquals(Stream<T> expected, Stream<T> toTest) {
    Iterator<T> iter = expected.iterator();
    toTest.forEach(x -> Assert.assertEquals(iter.next(), x));
    assertFalse(iter.hasNext());
  }
}
