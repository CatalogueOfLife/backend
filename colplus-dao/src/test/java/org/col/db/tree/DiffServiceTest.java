package org.col.db.tree;

import java.io.BufferedReader;
import java.io.File;

import org.apache.commons.io.IOUtils;
import org.col.common.io.Resources;
import org.col.dao.DaoTestBase;
import org.col.dao.DatasetImportDao;
import org.col.db.mapper.TestDataRule;
import org.junit.Assert;
import org.junit.Test;

public class DiffServiceTest extends DaoTestBase {
  static int attemptCnt;
  DiffService diff;
  DatasetImportDao dao;
  
  public DiffServiceTest() {
    super(TestDataRule.tree());
    dao = new DatasetImportDao(factory(), treeRepoRule.getRepo());
    diff = new DiffService(factory(), dao.getTreeDao());
  }
  
  
  @Test
  public void udiff() throws Exception {
    final File f1 = Resources.toFile("trees/coldp.tree");
    final File f2 = Resources.toFile("trees/coldp2.tree");
    
    BufferedReader br = diff.udiff(new int[]{1,2}, i -> {
      switch (i) {
        case 1: return f1;
        case 2: return f2;
      }
      return null;
    });
  
  
    String version = IOUtils.toString(br);
    System.out.println(version);
    
    Assert.assertTrue(version.startsWith("---"));
  }

}