package org.col.admin.importer.neo;

import java.io.IOException;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.neo.model.RelType;
import org.col.api.RandomUtils;
import org.col.api.model.Taxon;
import org.col.api.model.TermRecord;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.junit.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.*;


public class NeoDbTest {
  private final static int DATASET_KEY = 77;
  private final static NormalizerConfig cfg = new NormalizerConfig();

  NeoDb db;

  @BeforeClass
  public static void initRepo() {
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
  }

  @Before
  public void init() throws IOException {
    db = NeoDbFactory.create(DATASET_KEY, cfg);
  }

  @After
  public void destroy() {
    if (db != null) {
      db.closeAndDelete();
    }
  }

  @AfterClass
  public static void destroyRepo() {
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  /**
   * Tests inclusion of external cypher procedures for common graph algorithms.
   * See https://github.com/neo4j-contrib/neo4j-graph-algorithms/blob/3.3/tests/src/test/java/org/neo4j/graphalgo/algo/UnionFindProcIntegrationTest.java
   */
  @Test
  public void testUnionFind() throws Exception {
    String createGraph =
        "CREATE (nA:Label)\n" +
            "CREATE (nB:Label)\n" +
            "CREATE (nC:Label)\n" +
            "CREATE (nD:Label)\n" +
            "CREATE (nE)\n" +
            "CREATE (nF)\n" +
            "CREATE (nG)\n" +
            "CREATE (nH)\n" +
            "CREATE (nI)\n" +
            "CREATE (nJ)\n" + // {J}
            "CREATE\n" +

            // {A, B, C, D}
            "  (nA)-[:TYPE]->(nB),\n" +
            "  (nB)-[:TYPE]->(nC),\n" +
            "  (nC)-[:TYPE]->(nD),\n" +

            "  (nD)-[:TYPE {cost:4.2}]->(nE),\n" + // threshold UF should split here

            // {E, F, G}
            "  (nE)-[:TYPE]->(nF),\n" +
            "  (nF)-[:TYPE]->(nG),\n" +

            // {H, I}
            "  (nH)-[:TYPE]->(nI)";

      try (Transaction tx = db.getNeo().beginTx()) {
        db.getNeo().execute(createGraph).close();
        tx.success();
      }

    // graphImpl: Heavy, Light, Huge, Kernel
    String graphImpl = "Heavy";
    db.getNeo().execute("CALL algo.unionFind('', '',{graph:'" + graphImpl + "'}) YIELD setCount")
        .accept((Result.ResultVisitor<Exception>) row -> {
          assertEquals(3L, row.getNumber("setCount"));
          return true;
        });
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

  static class BatchProcException extends RuntimeException {
    public BatchProcException(String message) {
      super(message);
    }
  }

  @Test(expected = BatchProcException.class)
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

    db.process(Labels.ALL, 10, new NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        System.out.println("process " + n);
      }

      @Override
      public void commitBatch(int counter) {
        System.out.println("commitBatch "+counter);
      }
    });

    // now try with error throwing processor
    db.process(Labels.ALL, 10, new NodeBatchProcessor() {
      @Override
      public void process(Node n) {
        System.out.println("process " + n);
        if (n.getId() > 10) {
          throw new BatchProcException("I cannot count over ten!");
        }
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