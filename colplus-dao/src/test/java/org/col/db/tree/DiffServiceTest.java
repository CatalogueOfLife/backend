package org.col.db.tree;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.col.api.model.SectorImport;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DiffServiceTest {
  static int attemptCnt;
  
  @Test
  public void treediff() throws Exception {
    System.out.println(DiffService.treeDiff( syncImp("coldp.tree"), syncImp("coldp2.tree")));
  }
  
  @Test
  public void namesdiff() throws Exception {
    DiffReport.NamesDiff diff = DiffService.namesDiff( syncImp("c1", "c2", "3", "dghz"), syncImp("f1", "c2", "32"));
    assertEquals(3, diff.getDeleted().size());
    assertTrue(diff.getDeleted().contains("3"));
  
    assertEquals(2, diff.getInserted().size());
    assertTrue(diff.getInserted().contains("f1"));
  }
  
  private static SectorImport syncImp(String... ids) throws IOException {
    SectorImport si = new SectorImport();
    si.setSectorKey(1);
    si.setAttempt(attemptCnt++);
    si.setNames(new HashSet<>());
    for (String id : ids) {
      si.getNames().add(id);
    }
    return si;
  }
  
  private static SectorImport syncImp(String fn) throws IOException {
    SectorImport si = new SectorImport();
    si.setSectorKey(1);
    si.setAttempt(attemptCnt++);
    InputStream tree = DiffServiceTest.class.getResourceAsStream("/trees/"+fn);
    si.setTextTree(IOUtils.toString(tree, Charsets.UTF_8).trim());
    return si;
  }
}