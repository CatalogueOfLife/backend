package life.catalogue.importer.neo;

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

import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.GbifTerm;

import java.io.IOException;
import java.util.function.BiConsumer;

import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import com.google.common.io.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class NeoDbTest {
  private int datasetKey;
  private final static NormalizerConfig cfg = new NormalizerConfig();
  private static NeoDbFactory neoDbFactory;

  NeoDb db;
  
  @BeforeClass
  public static void initRepo() {
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    neoDbFactory = new NeoDbFactory(cfg);
  }
  
  @Before
  public void init() throws IOException {
    datasetKey = RandomUtils.randomInt();
    System.out.println("Use datasetKey "+datasetKey);
    db = neoDbFactory.create(datasetKey, 1);
  }
  
  @After
  public void destroy() {
    if (db != null) {
      db.close();
    }
  }
  
  @AfterClass
  public static void destroyRepo() throws Exception {
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }
  
  @Test
  public void neoSync() throws Exception {
    NeoUsage u1;
    NeoUsage u2;
    Name n1;
    try (Transaction tx = db.getNeo().beginTx()) {
      u1 = taxon("12");
      n1 = u1.usage.getName();
      db.createNameAndUsage(u1, tx);
      assertNull(u1.usage.getName());

      u2 = taxon("13");
      db.createNameAndUsage(u2, tx);
      assertNull(u2.usage.getName());

      // now relate the 2 nodes and make sure when we read the relations the instance is changed accordingly
      u1.node.createRelationshipTo(u2.node, RelType.PARENT_OF);
      u2.nameNode.createRelationshipTo(u1.nameNode, RelType.HAS_BASIONYM);

      tx.commit();
    }
    db.sync();
    
    try (Transaction tx = db.getNeo().beginTx()) {
      NeoName n1b = db.names().objByID("12", tx);
      NeoName n2b = db.names().objByID("13", tx);
      assertEquals(n1, n1b.getName());

      NeoUsage u1b = db.usages().objByID("12", tx);
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
        db.createNameAndUsage(u, tx);
        if (p == null) {
          p = u;
        } else if (p2 == null || i%10==0) {
          p2 = u;
          p.node.createRelationshipTo(p2.node, RelType.PARENT_OF);
          
        } else {
          p2.node.createRelationshipTo(u.node, RelType.PARENT_OF);
        }
      }
      tx.commit();
    }
    db.sync();

    db.process(null, new BiConsumer<Node, Transaction>() {
      @Override
      public void accept(Node n, Transaction transaction) {
        System.out.println("process node " + NeoDbUtils.id(n));
      }
    });
    
    // now try with error throwing processor
    db.process(null, new BiConsumer<Node, Transaction>() {
      @Override
      public void accept(Node n, Transaction transaction) {
        System.out.println("process node " + NeoDbUtils.id(n));
        if (n.getId() > 10) {
          throw new BatchProcException("I cannot count over ten!");
        }
      }
    });
  }
  
  @Test
  public void updateTaxon() throws Exception {
    try (Transaction tx = db.getNeo().beginTx()) {
      NeoUsage u = taxon("id1");
      db.createNameAndUsage(u, tx);
      tx.commit();
    }
    db.sync();
    
    VerbatimRecord tr = new VerbatimRecord(123, "bla.txt", GbifTerm.VernacularName);
    tr.setType(AcefTerm.Distribution);
    tr.put(AcefTerm.DistributionElement, "Asia");
    
    try (Transaction tx = db.getNeo().beginTx()) {
      NeoUsage u = db.usages().objByID("id1", tx);
      db.usages().update(u, tx);
    }
    
    try (Transaction tx = db.getNeo().beginTx()) {
      NeoUsage u = db.usages().objByID("id1", tx);
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

      tx.commit();
    }
  }

  public static NeoUsage taxon(String id) {
    NeoUsage t = NeoUsage.createTaxon(Origin.SOURCE, TaxonomicStatus.ACCEPTED);
    t.usage.setName(RandomUtils.randomName());
    t.setId(id);
    return t;
  }
}