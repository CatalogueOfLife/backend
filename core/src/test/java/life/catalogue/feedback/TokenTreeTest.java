package life.catalogue.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenTreeTest {

  @Test
  void testTree() {
    var tree = new TokenTree();
    tree.add(new String[]{"foo","bar"});
    tree.add(new String[]{"foo","car"});
    tree.add(new String[]{"loo","foo","bar"});
    tree.add(new String[]{"poo","bar"});
    tree.add(new String[]{"poo"}); // removes above

    assertEquals(7, tree.size());

    var n = tree.get("poo");
    assertTrue(n.isTerminal());

    n = tree.get("foo");
    assertFalse(n.isTerminal());
    assertEquals(2, n.size());

    n = n.get("car");
    assertTrue(n.isTerminal());
  }

}