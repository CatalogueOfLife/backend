package org.col.commands.importer.txttree;

import org.apache.commons.io.IOUtils;
import org.gbif.nameparser.api.Rank;
import org.gbif.utils.file.FileUtils;
import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.*;


public class TreeTest {

  @Test
  public void testRead() throws Exception {
    Tree tree = Tree.read("trees/test.txt");

    tree.print(System.out);

    StringWriter buffer = new StringWriter();
    tree.print(buffer);
    assertEquals(buffer.toString().trim(), IOUtils.toString(FileUtils.classpathStream("trees/test.txt"), "UTF8").trim());


    System.out.println("Tree traversal");
    for (TreeNode n : tree) {
      assertNotNull(n.name);
      assertEquals(Rank.UNRANKED, n.rank);
    }
  }

  @Test
  public void testRead2() throws Exception {
    Tree tree = Tree.read("trees/test2.txt");

    tree.print(System.out);

    StringWriter buffer = new StringWriter();
    tree.print(buffer);
    assertEquals(IOUtils.toString(FileUtils.classpathStream("trees/test2.txt"), "UTF8").trim(), buffer.toString().trim());


    System.out.println("Tree traversal");
    for (TreeNode n : tree) {
      System.out.println(n.name);
      assertNotNull(n.name);
      assertNotNull(n.rank);
    }
  }

}