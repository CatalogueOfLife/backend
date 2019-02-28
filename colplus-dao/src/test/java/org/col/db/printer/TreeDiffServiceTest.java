package org.col.db.printer;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class TreeDiffServiceTest {
  
  @Test
  public void diff() throws Exception {
    System.out.println(TreeDiffService.diff( 1, 1, tree("coldp.tree"), 2, tree("coldp2.tree")));
  }
  
  private String tree(String fn) throws IOException {
    InputStream tree = getClass().getResourceAsStream("/trees/"+fn);
    return IOUtils.toString(tree, Charsets.UTF_8).trim();
  }
}