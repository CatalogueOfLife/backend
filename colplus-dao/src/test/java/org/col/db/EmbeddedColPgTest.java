package org.col.db;

import org.junit.Test;

import static org.junit.Assert.*;

public class EmbeddedColPgTest {

  @Test
  public void startStop() {
    EmbeddedColPg pg = new EmbeddedColPg();
    pg.start();
    pg.stop();
  }
}