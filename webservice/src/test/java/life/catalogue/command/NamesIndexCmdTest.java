package life.catalogue.command;

import life.catalogue.matching.nidx.NameIndexMapStore;
import life.catalogue.matching.nidx.NameIndexStore;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NamesIndexCmdTest {

  @Test
  public void store() throws Exception {
    NameIndexStore store = new NameIndexMapStore();
    store.start();
    store.add("asdfgh", 778899);
    assertEquals(778899, store.get("asdfgh"));
    assertTrue(store.contains("asdfgh"));
    store.stop();
  }

}
