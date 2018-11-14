package org.col.admin.importer.neo;

import java.io.IOException;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.model.NeoName;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.neo.model.RelType;
import org.col.api.RandomUtils;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.Origin;
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
    NeoUsage t1;
    NeoUsage t2;
    try (Transaction tx = db.getNeo().beginTx()) {
      t1 = taxon("12");
      db.createNameAndUsage(t1);
      t2 = taxon("13");
      db.createNameAndUsage(t2);

      // now relate the 2 nodes and make sure when we read the relations the instance is changed accordingly
      t1.node.createRelationshipTo(t2.node, RelType.PARENT_OF);
      t2.node.createRelationshipTo(t1.node, RelType.HAS_BASIONYM);

      assertNull(t1.usage.getName().getHomotypicNameId());
      assertNull(t2.usage.getName().getHomotypicNameId());

      tx.success();
    }
    db.sync();

    try (Transaction tx = db.getNeo().beginTx()) {
      NeoName t1b = db.names().objByID("12");
      NeoName t2b = db.names().objByID("13");
      assertNotNull(t1b.name.getHomotypicNameId());
      assertNotNull(t2b.name.getHomotypicNameId());

      assertEquals(t1b.name.getHomotypicNameId(), t2b.name.getHomotypicNameId());
      t1b.name.setHomotypicNameId(null);
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
      NeoUsage p = null;
      NeoUsage p2 = null;
      for (int i = 1; i<100; i++) {
        NeoUsage u = taxon("id-"+i);
        db.createNameAndUsage(u);
        if (p == null) {
          p = u;
        } else if (p2 == null || i%10==0) {
          p2 = u;
          p.node.createRelationshipTo(p2.node, RelType.PARENT_OF);

        } else {
          p2.node.createRelationshipTo(u.node, RelType.PARENT_OF);
        }
      }
      tx.success();
    }
    db.sync();

    db.process(null, 10, new NodeBatchProcessor() {
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
    db.process(null, 10, new NodeBatchProcessor() {
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
      NeoUsage u = taxon("id1");
      db.createNameAndUsage(u);
      tx.success();
    }
    db.sync();

    VerbatimRecord tr = new VerbatimRecord(123, "bla.txt", GbifTerm.VernacularName);
    tr.setType(AcefTerm.Distribution);
    tr.put(AcefTerm.DistributionElement, "Asia");

    try (Transaction tx = db.getNeo().beginTx()) {
      NeoUsage u = db.usages().objByID("id1");
      db.usages().update(u);
    }

    try (Transaction tx = db.getNeo().beginTx()) {
      NeoUsage u = db.usages().objByID("id1");
      //assertEquals(1, t.verbatim.getExtensionRecords(AcefTerm.Distribution).size());
      //assertEquals(tr, t.verbatim.getExtensionRecords(AcefTerm.Distribution).getUsage(0));
    }

  }

  public static NeoUsage taxon(String id) {
    NeoUsage t = NeoUsage.createTaxon(Origin.SOURCE, false);
    t.usage.setName(RandomUtils.randomName());
    t.setId(id);
    return t;
  }
}