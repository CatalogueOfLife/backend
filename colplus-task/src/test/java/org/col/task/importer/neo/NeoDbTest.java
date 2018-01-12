package org.col.task.importer.neo;

import com.google.common.io.Files;
import org.col.api.RandomUtils;
import org.col.api.Taxon;
import org.col.api.vocab.TaxonomicStatus;
import org.col.task.common.NormalizerConfig;
import org.col.task.importer.neo.model.Labels;
import org.col.task.importer.neo.model.NeoTaxon;
import org.col.task.importer.neo.model.RelType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphdb.Node;
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
  public void UpdateTaxonStoreWithRelations() throws Exception {
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
      assertEquals((long) t2b.name.getBasionymKey(), t1.node.getId());
    }
  }

  @Test
  public void batchProcessing() throws Exception {
    try (Transaction tx = db.getNeo().beginTx()) {
      NeoTaxon p = null;
      NeoTaxon p2 = null;
      for (int i = 1; i<100; i++) {
        NeoTaxon t = db.put(taxon("id-"+i));
        if (p == null) {
          p = t;
        } else if (p2 == null || i%10==0) {
          p2 = t;
          p.node.createRelationshipTo(p2.node, RelType.PARENT_OF);

        } else {
          p2.node.createRelationshipTo(t.node, RelType.PARENT_OF);
        }
      }
      tx.success();
    }
    db.updateLabels();
    db.updateTaxonStoreWithRelations();
    db.process(Labels.ALL, 5, new NeoDb.NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        System.out.println("process " + n);
      }

      @Override
      public void commitBatch(int counter) {
        System.out.println("commitBatch "+counter);
      }
    });
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