package org.col.commands.importer.neo;

import com.google.common.io.Files;
import org.col.commands.config.NormalizerConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

/**
 *
 */
@RunWith(Parameterized.class)
@Ignore("Need to update to modified Name class without canonical name")
public class NeoDbTest {
  private final static Random RND = new Random();
  private final static int DATASET_KEY = 123;
  private final static NormalizerConfig cfg = new NormalizerConfig();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    cfg.directory = Files.createTempDir();

    return Arrays.asList(new Object[][]{
        {false},
        {true}
    });
  }

  boolean persistent;
  NeoDb db;

  public NeoDbTest(boolean persistent) {
    this.persistent = persistent;
  }

  @Before
  public void init() throws IOException {
    if (persistent) {
      db = NeoDbFactory.create(cfg, DATASET_KEY);
    } else {
      db = NeoDbFactory.temporaryDb(10);
    }
  }

  @After
  public void destroy() {
    if (db != null) {
      db.closeAndDelete();
    }
  }

  @Test
  public void testBasicDb() throws Exception {
    //TODO
  }
}