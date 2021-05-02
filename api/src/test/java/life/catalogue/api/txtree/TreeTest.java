package life.catalogue.api.txtree;

import life.catalogue.common.io.Resources;
import org.apache.commons.io.IOUtils;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.*;


public class TreeTest {

  @Test
  public void testVerify() throws Exception {
    assertTrue(Tree.verify(Resources.stream("txtree/test.txt")));
    assertTrue(Tree.verify(Resources.stream("txtree/test2.txt")));

    assertFalse(Tree.verify(Resources.stream("txtree/badtree.txt")));
    assertFalse(Tree.verify(Resources.stream("txtree/notree.txt")));
    assertFalse(Tree.verify(Resources.stream("txtree/notree2.txt")));
  }

  @Test
  public void testRead() throws Exception {
    Tree tree = Tree.read(Resources.stream("txtree/test.txt"));

    tree.print(System.out);

    StringWriter buffer = new StringWriter();
    tree.print(buffer);
    assertEquals(buffer.toString().trim(), IOUtils.toString(Resources.stream("txtree/test.txt"), "UTF8").trim());


    System.out.println("Tree traversal");
    for (TreeNode n : tree) {
      assertNotNull(n.name);
      assertEquals(Rank.UNRANKED, n.rank);
    }
  }

  @Test
  public void testRead2() throws Exception {
    Tree tree = Tree.read(Resources.stream("txtree/test2.txt"));

    tree.print(System.out);

    StringWriter buffer = new StringWriter();
    tree.print(buffer);
    assertEquals(IOUtils.toString(Resources.stream("txtree/test2-no-comments.txt"), "UTF8").trim(), buffer.toString().trim());


    System.out.println("Tree traversal");
    for (TreeNode n : tree) {
      System.out.println(n.name);
      assertNotNull(n.name);
      assertNotNull(n.rank);
    }
  }

}