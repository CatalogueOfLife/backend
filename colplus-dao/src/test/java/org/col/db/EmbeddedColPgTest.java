package org.col.db;

import org.col.common.util.YamlUtils;
import org.junit.Test;

import java.io.IOException;

public class EmbeddedColPgTest {
  
  @Test
  //@Ignore
  public void startStop() throws IOException {
    PgConfig cfg = YamlUtils.read(PgConfig.class, "/pg-test.yaml");
    EmbeddedColPg pg = new EmbeddedColPg(cfg);
    pg.start();
    pg.stop();
  }
}