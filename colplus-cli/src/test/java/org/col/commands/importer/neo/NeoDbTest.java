package org.col.commands.importer.neo;

import com.google.common.io.Files;
import org.col.api.RandomUtils;
import org.col.api.Taxon;
import org.col.api.vocab.TaxonomicStatus;
import org.col.commands.config.NormalizerConfig;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.commands.importer.neo.model.RelType;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
  public void testPutTaxon() throws Exception {
    NeoTaxon t1;
    NeoTaxon t2;
    try (Transaction tx = db.getNeo().beginTx()) {
      t1 = db.put(taxon("12"));
      t2 = db.put(taxon("13"));

      // now relate the 2 nodes and make sure when we read the relations the instance is changed accordingly
      t1.node.createRelationshipTo(t2.node, RelType.PARENT_OF);
      t1.node.createRelationshipTo(t2.node, RelType.BASIONYM_OF);
      assertNull(t2.name.getBasionymKey());

      tx.success();
    }
    db.updateLabels();
    db.updateTaxonStoreWithRelations();

    try (Transaction tx = db.getNeo().beginTx()) {
      NeoTaxon t1b = db.get(db.byTaxonID("12"));
      assertEquals(t1, t1b);

      NeoTaxon t2b = db.get(db.byTaxonID("13"));
      assertEquals(t2b.name.getBasionymKey(), t1.name);
    }
  }

  public static NeoTaxon taxon(String id) {
    NeoTaxon t = new NeoTaxon();
    t.name = RandomUtils.randomName();
    t.taxon = new Taxon();
    t.taxon.setId(id);
    t.taxon.setStatus(TaxonomicStatus.ACCEPTED);
    return t;
  }
}