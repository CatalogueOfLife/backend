package org.col.db;

import java.io.IOException;
import org.col.common.util.YamlUtils;
import org.junit.Ignore;
import org.junit.Test;

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