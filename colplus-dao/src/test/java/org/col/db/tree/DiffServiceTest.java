package org.col.db.tree;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.col.api.model.SectorImport;
import org.junit.Test;

public class DiffServiceTest {
  static int attemptCnt;
  
  @Test
  public void treediff() throws Exception {
    System.out.println(DiffService.treeDiff( syncImp("coldp.tree"), syncImp("coldp2.tree")));
  }
  
  @Test
  public void namesdiff() throws Exception {
    System.out.println(DiffService.namesDiff( syncImp("c1", "c2", "3", "dghz"), syncImp("f1", "c2", "32")));
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