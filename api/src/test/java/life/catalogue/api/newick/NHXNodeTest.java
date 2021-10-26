package life.catalogue.api.newick;

import org.junit.Test;

import static org.junit.Assert.*;

public class NHXNodeTest {

  @Test
  public void repl() {
    assertEquals("asdfghjk", NHXNode.repl("asdfghjk"));
    assertEquals("Döring_1966", NHXNode.repl("Döring 1966"));
    assertEquals("Döring__1966_", NHXNode.repl("Döring  1966 "));
    assertEquals("'Döring ''1966'", NHXNode.repl("Döring '1966"));
    assertEquals("'Döring (1966)'", NHXNode.repl("Döring (1966)"));
  }
}