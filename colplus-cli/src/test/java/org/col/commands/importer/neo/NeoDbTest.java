package org.col.commands.importer.neo;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.col.api.Name;
import org.col.api.Taxon;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.col.commands.config.NormalizerConfig;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.RankedName;
import org.col.commands.importer.neo.model.RelType;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

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
  public void init() {
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