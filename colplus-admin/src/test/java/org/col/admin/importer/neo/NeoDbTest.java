package org.col.admin.importer.neo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import com.google.common.io.Files;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.neo.model.RelType;
import org.col.api.RandomUtils;
import org.col.api.model.Taxon;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.*;

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
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();

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
      db = NeoDbFactory.create(DATASET_KEY, cfg);
    } else {
      db = NeoDbFactory.temporaryDb(1, 10);
    }
  }

  @After
  public void destroy() {
    if (db != null) {
      db.closeAndDelete();
    }
  }

  @Test
  public void neoSync() throws Exception {
    NeoTaxon t1;
    NeoTaxon t2;
    try (Transaction tx = db.getNeo().beginTx()) {
      t1 = db.put(taxon("12"));
      t2 = db.put(taxon("13"));

      // now relate the 2 nodes and make sure when we read the relations the instance is changed accordingly
      t1.node.createRelationshipTo(t2.node, RelType.PARENT_OF);
      t2.node.createRelationshipTo(t1.node, RelType.HAS_BASIONYM);

      assertNull(t1.name.getHomotypicNameKey());
      assertNull(t2.name.getHomotypicNameKey());

      tx.success();
    }
    db.sync();

    try (Transaction tx = db.getNeo().beginTx()) {
      NeoTaxon t1b = db.get(db.byID("12"));
      NeoTaxon t2b = db.get(db.byID("13"));
      assertNotNull(t1b.name.getHomotypicNameKey());
      assertNotNull(t2b.name.getHomotypicNameKey());

      assertEquals(t1b.name.getHomotypicNameKey(), t2b.name.getHomotypicNameKey());
      t1b.name.setHomotypicNameKey(null);
      assertEquals(t1, t1b);
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
    db.sync();
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

  @Test
  public void updateTaxon() throws Exception {
    try (Transaction tx = db.getNeo().beginTx()) {
      NeoTaxon t = db.put(taxon("id1"));
      tx.success();
    }
    db.sync();

    TermRecord tr = new TermRecord(123, "bla.txt", GbifTerm.VernacularName);
    tr.setType(AcefTerm.Distribution);
    tr.put(AcefTerm.DistributionElement, "Asia");

    try (Transaction tx = db.getNeo().beginTx()) {
      NeoTaxon t = db.getByID("id1");

      db.update(t);
    }

    try (Transaction tx = db.getNeo().beginTx()) {
      NeoTaxon t = db.getByID("id1");
      //assertEquals(1, t.verbatim.getExtensionRecords(AcefTerm.Distribution).size());
      //assertEquals(tr, t.verbatim.getExtensionRecords(AcefTerm.Distribution).get(0));
    }

  }

  public static NeoTaxon taxon(String id) {
    NeoTaxon t = new NeoTaxon();
    t.name = RandomUtils.randomName();
    t.taxon = new Taxon();
    t.taxon.setId(id);
    return t;
  }
}