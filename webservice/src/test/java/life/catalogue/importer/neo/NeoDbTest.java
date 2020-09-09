package life.catalogue.importer.neo;

import com.google.common.io.Files;
import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import org.apache.commons.io.FileUtils;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.junit.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;

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
    db = NeoDbFactory.create(DATASET_KEY, 1, cfg);
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
    NeoUsage u1;
    NeoUsage u2;
    Name n1;
    try (Transaction tx = db.getNeo().beginTx()) {
      u1 = taxon("12");
      assertNull(u1.usage.getName().getHomotypicNameId());
      n1 = u1.usage.getName();
      db.createNameAndUsage(u1);
      assertNull(u1.usage.getName());

      u2 = taxon("13");
      assertNull(u2.usage.getName().getHomotypicNameId());
      db.createNameAndUsage(u2);
      assertNull(u2.usage.getName());

      // now relate the 2 nodes and make sure when we read the relations the instance is changed accordingly
      u1.node.createRelationshipTo(u2.node, RelType.PARENT_OF);
      u2.nameNode.createRelationshipTo(u1.nameNode, RelType.HAS_BASIONYM);

      tx.success();
    }
    db.sync();
    
    try (Transaction tx = db.getNeo().beginTx()) {
      NeoName n1b = db.names().objByID("12");
      NeoName n2b = db.names().objByID("13");
      assertNotNull(n1b.getName().getHomotypicNameId());
      assertNotNull(n2b.getName().getHomotypicNameId());

      assertEquals(n1b.getName().getHomotypicNameId(), n2b.getName().getHomotypicNameId());
      
      n1b.getName().setHomotypicNameId(null);
      assertEquals(n1, n1b.getName());

      NeoUsage u1b = db.usages().objByID("12");
      assertEquals(u1, u1b);
    }
  }
  
  static class BatchProcException extends RuntimeException {
    BatchProcException(String message) {
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
        System.out.println("commitBatch " + counter);
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
        System.out.println("commitBatch " + counter);
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
  
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/389
   */
  @Test
  public void createCyrillicRef() throws Exception {
    try (Transaction tx = db.getNeo().beginTx()) {
      // this citation has a nearly invisible cyrillic o that cannot be folded into ASCII
      Reference r = TestEntityGenerator.newReference();
      r.setCitation("Contribuciоnes al conocimiento de la flora del Gondwana Superior en la Argentina. XXXIII \"Ginkgoales\" de los Estratos de Potrerillos en la Precordillera de Mendoza.");
      db.references().create(r);
  
      r = TestEntityGenerator.newReference();
      r.setCitation("Mandarin:哦诶艾诶艾哦屁杰诶  Japanese:ｪｺｻｪ ｷｼｪｩｪ ｺｪｹ ｻｼ ｴｮｨｱ  Other: ወለi էዠለi   mබƖ tƕබƖ   ꀪꋬꊛ ꓄ꈚꋬꊛ");
      db.references().create(r);

      tx.success();
    }
  }

  public static NeoUsage taxon(String id) {
    NeoUsage t = NeoUsage.createTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);
    t.usage.setName(RandomUtils.randomName());
    t.setId(id);
    return t;
  }
}