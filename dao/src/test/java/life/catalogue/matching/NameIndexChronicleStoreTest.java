package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndexChronicleStore;
import life.catalogue.matching.nidx.NameIndexStore;

import java.io.IOException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameIndexChronicleStoreTest extends NameIndexStoreTest {

  @Override
  NameIndexStore create() throws IOException {
    return new NameIndexChronicleStore(cfg);
  }

  /**
   * The persistent chronicle store must survive a stop/reopen against the same directory.
   */
  @Test
  public void persistenceAcrossRestart() throws Exception {
    db.add("abies alb", 1);
    db.add("picea", 5);
    db.add("larus", 9);
    assertEquals(3, db.count());

    db.stop();
    db = create();
    db.start();

    assertEquals(3, db.count());
    assertEquals(9, db.maxKey());
    assertEquals(1, db.get("abies alb"));
    assertEquals(9, db.get("larus"));
  }
}
