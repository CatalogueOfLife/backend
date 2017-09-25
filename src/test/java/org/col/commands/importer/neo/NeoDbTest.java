package org.col.commands.importer.neo;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.col.api.Name;
import org.col.api.Taxon;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Rank;
import org.col.api.vocab.TaxonomicStatus;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.RankedName;
import org.col.commands.importer.neo.model.RelType;
import org.col.commands.importer.neo.model.TaxonNameNode;
import org.col.config.NeoConfig;
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
  private final static NeoConfig cfg = new NeoConfig();

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    cfg.neoRepository = Files.createTempDir();

    return Arrays.asList(new Object[][]{
        {false},
        {true}
    });
  }

  boolean persistent;
  NeoDb<TaxonNameNode> db;

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
    testDb();
  }

  /**
   * Test if reopening a persistent db works.
   */
  @Test
  public void persistentcyTest() throws Exception {
    testDb();
    // close and reopen. Make sure data survived
    db.close();
    try {
      db = NeoDbFactory.open(cfg, DATASET_KEY);
      try (Transaction tx = db.beginTx()) {
        TaxonNameNode u3 = usage(300, Rank.SPECIES);
        Node n3 = db.create(u3);
        u3.setNode(n3);
        assertEquals(u3, db.read(n3, false));
        // expect previous data to remain
        verifyData(true, db.getNeo().getNodeById(0), db.getNeo().getNodeById(1));
      }

      if (!persistent) {
        fail("It should not be possible to reopen a temporary db");
      }

    } catch (RuntimeException e) {
      if (persistent) {
        throw e;
      }
    }
  }

  private void testDb() throws Exception {
    try (Transaction tx = db.beginTx()) {
      Node n1 = db.create(usage(112, Rank.GENUS));
      Node n2 = db.create(usage(200, Rank.SPECIES));

      verifyData(false, n1, n2);

      // now relate the 2 nodes and make sure when we read the relations the instance is changed accordingly
      n1.createRelationshipTo(n2, RelType.PARENT_OF);
      n1.createRelationshipTo(n2, RelType.BASIONYM_OF);
      verifyData(true, n1, n2);
      tx.success();
    }
  }

  private void verifyData(boolean expectRels, Node n1, Node n2) throws Exception {
    final TaxonNameNode u2 = usage(200, Rank.SPECIES);
    assertEquals(usage(112, Rank.GENUS), Preconditions.checkNotNull(readExclNode(n1, false), "Usage 1 missing"));
    assertEquals(usage(112, Rank.GENUS), Preconditions.checkNotNull(readExclNode(n1, true), "Usage 1 missing"));
    assertEquals(u2, Preconditions.checkNotNull(readExclNode(n2, false), "Usage 2 missing"));
    if (expectRels) {
      u2.setParentKey((int) n1.getId());
      u2.setBasionymKey((int) n1.getId());
      u2.setBasionym("Abies alba Mill.");
    }
    assertEquals(u2, Preconditions.checkNotNull(readExclNode(n2, true), "Usage 2 missing"));
  }

  private TaxonNameNode readExclNode(Node n, boolean readRelations) {
    TaxonNameNode tnn = db.read(n, readRelations);
    tnn.setNode(null);
    return tnn;
  }
/*

  @Test
  @Ignore("manual test to generate GML test files for rod pages forest diff tool")
  public void testTrees2() throws Exception {
    try (ClasspathSource src = new ClasspathSource(41);) {
      src.init(true, false, false, false);
      db = src.getDao();

      // add pro parte & basionym rel
      try (Transaction tx = db.beginTx()) {
        Node ppsyn = db.findByNameSingle("Acromantis javana");
        Node acc2 = db.findByNameSingle("Acromantis montana");
        ppsyn.createRelationshipTo(acc2, RelType.PROPARTE_SYNONYM_OF);
        // basionym
        ppsyn.createRelationshipTo(acc2, RelType.BASIONYM_OF);
        ppsyn.addLabel(Labels.BASIONYM);

        tx.success();
      }

      Writer writer = FileUtils.startNewUtf8File(new File("/Users/markus/Desktop/test.txt"));
      try (Transaction tx = db.beginTx()) {
        db.printTree(writer, GraphFormat.LIST, true, Rank.SUBGENUS, null);
      }
      writer.flush();
    }
  }

  @Test
  public void testTrees() throws Exception {
    try (ClasspathSource src = new ClasspathSource(41);) {
      src.init(true, false, false, false);
      db = src.getDao();

      // add pro parte & basionym rel
      try (Transaction tx = db.beginTx()) {
        Node ppsyn = db.findByNameSingle("Acromantis javana");
        Node acc2 = db.findByNameSingle("Acromantis montana");
        ppsyn.createRelationshipTo(acc2, RelType.PROPARTE_SYNONYM_OF);
        // basionym
        ppsyn.createRelationshipTo(acc2, RelType.BASIONYM_OF);
        ppsyn.addLabel(Labels.BASIONYM);

        tx.success();
      }

      List<Rank> ranks = Lists.newArrayList((Rank) null);
      ranks.addAll(Rank.LINNEAN_RANKS);

      Writer writer = new PrintWriter(System.out);
      for (GraphFormat format : GraphFormat.values()) {
        for (int bool = 1; bool > 0; bool--) {
          for (Rank rank : ranks) {
            try (Transaction tx = db.beginTx()) {
              writer.write("\n" + org.apache.commons.lang3.StringUtils.repeat("+", 60) + "\n");
              writer.write("Format=" + format + ", rank=" + rank + ", fullNames=" + (bool == 1) + "\n");
              writer.write(org.apache.commons.lang3.StringUtils.repeat("+", 80) + "\n");
              db.printTree(writer, format, bool == 1, rank, null);
            } catch (IllegalArgumentException e) {
              if (format != GraphFormat.GML && format != GraphFormat.TAB) {
                Throwables.propagate(e);
              }
            }
          }
        }
      }
      writer.flush();
    }
  }

*/

  @Test
  public void testNodeByTaxonId() throws Exception {
    try (Transaction tx = db.beginTx()) {
      assertNull(db.nodeByTaxonId("312"));

      TaxonNameNode tnn = create("312", "Abies", "Abies Mill.");
      tx.success();

      assertEquals(tnn.getNode(), db.nodeByTaxonId("312"));
      assertNull(db.nodeByTaxonId("412"));
    }
  }

  @Test
  public void testNodeByCanonical() throws Exception {
    try (Transaction tx = db.beginTx()) {
      assertNull(db.nodeByCanonical("Abies"));

      TaxonNameNode tnn = create("312", "Abies", "Abies Mill.");
      tx.success();

      assertEquals(tnn.getNode(), db.nodeByCanonical("Abies"));
      //assertEquals(node, db.nodeByCanonical("abies"));
      assertNull(db.nodeByCanonical("Abiess"));
    }
  }

  @Test
  public void testNodesByCanonical() throws Exception {
    try (Transaction tx = db.beginTx()) {
      assertEquals(0, db.nodesByCanonical("Abies").size());

      create("312", "Abies", "Abies Mill.");
      tx.success();
      assertEquals(1, db.nodesByCanonical("Abies").size());

      create("313", "Abies", "Abies Mill.");
      tx.success();
      assertEquals(2, db.nodesByCanonical("Abies").size());
    }
  }

  @Test
  public void testNodeBySciname() throws Exception {
    try (Transaction tx = db.beginTx()) {
      assertNull(db.nodeBySciname("Abies Mill."));

      Node n = create("312", "Abies", "Abies Mill.").getNode();
      tx.success();

      assertEquals(n, db.nodeBySciname("Abies Mill."));
      assertNull(db.nodeBySciname("Abies"));
    }
  }

  @Test
  public void testCreateTaxon() throws Exception {
    try (Transaction tx = db.beginTx()) {
      assertNull(db.nodeBySciname("Abies Mill."));

      Node n = create(Origin.DENORMED_CLASSIFICATION, "Abies Mill.", Rank.GENUS, TaxonomicStatus.ACCEPTED, false).node;
      tx.success();

      assertEquals(n, db.nodeBySciname("Abies Mill."));
      assertNull(db.nodeBySciname("Abies"));
    }
  }

  @Test
  public void testHighestParent() throws Exception {
    try (Transaction tx = db.beginTx()) {

      Node n = create(Origin.DENORMED_CLASSIFICATION, "Abies Mill.", Rank.GENUS, TaxonomicStatus.ACCEPTED, false).node;
      tx.success();

      assertEquals(n, db.getDirectParent(n).node);


      Node syn = create(Origin.DENORMED_CLASSIFICATION, "Pinus", Rank.GENUS, TaxonomicStatus.SYNONYM, false).node;
      Node n2 = create(Origin.DENORMED_CLASSIFICATION, "Pinaceae", Rank.FAMILY, TaxonomicStatus.ACCEPTED, false).node;
      Node n3 = create(Origin.DENORMED_CLASSIFICATION, "Pinales", Rank.ORDER, TaxonomicStatus.ACCEPTED, false).node;
      Node n4 = create(Origin.DENORMED_CLASSIFICATION, "Plantae", Rank.KINGDOM, TaxonomicStatus.ACCEPTED, false).node;
      n4.createRelationshipTo(n3, RelType.PARENT_OF);
      n3.createRelationshipTo(n2, RelType.PARENT_OF);
      n2.createRelationshipTo(n, RelType.PARENT_OF);
      syn.createRelationshipTo(n, RelType.SYNONYM_OF);
      tx.success();

      assertEquals(n4, db.getDirectParent(n).node);
    }
  }

  @Test
  public void testMatchesClassification() throws Exception {
    try (Transaction tx = db.beginTx()) {

      Node n = create(Origin.DENORMED_CLASSIFICATION, "Abies Mill.", Rank.GENUS, TaxonomicStatus.ACCEPTED, false).node;
      Node syn = create(Origin.DENORMED_CLASSIFICATION, "Pinus", Rank.GENUS, TaxonomicStatus.SYNONYM, false).node;
      Node n2 = create(Origin.DENORMED_CLASSIFICATION, "Pinaceae", Rank.FAMILY, TaxonomicStatus.ACCEPTED, false).node;
      Node n3 = create(Origin.DENORMED_CLASSIFICATION, "Pinales", Rank.ORDER, TaxonomicStatus.ACCEPTED, false).node;
      Node n4 = create(Origin.DENORMED_CLASSIFICATION, "Plantae", Rank.KINGDOM, TaxonomicStatus.ACCEPTED, false).node;
      n4.createRelationshipTo(n3, RelType.PARENT_OF);
      n3.createRelationshipTo(n2, RelType.PARENT_OF);
      n2.createRelationshipTo(n, RelType.PARENT_OF);
      syn.createRelationshipTo(n, RelType.SYNONYM_OF);
      tx.success();

      List<RankedName> classification = Lists.newArrayList();
      assertTrue(db.matchesClassification(n4, classification));
      assertFalse(db.matchesClassification(n3, classification));
      assertFalse(db.matchesClassification(n2, classification));
      assertFalse(db.matchesClassification(n, classification));
//      assertFalse(db.matchesClassification(syn, classification));

      classification.add(new RankedName("Plantae", Rank.KINGDOM));
      assertFalse(db.matchesClassification(n4, classification));
      assertTrue(db.matchesClassification(n3, classification));
      assertFalse(db.matchesClassification(n2, classification));
      assertFalse(db.matchesClassification(n, classification));

      classification.add(0, new RankedName("Pinales", Rank.ORDER));
      assertFalse(db.matchesClassification(n4, classification));
      assertFalse(db.matchesClassification(n3, classification));
      assertTrue(db.matchesClassification(n2, classification));
      assertFalse(db.matchesClassification(n, classification));

      classification.add(0, new RankedName("Pinaceae", Rank.SUBFAMILY));
      assertFalse(db.matchesClassification(n4, classification));
      assertFalse(db.matchesClassification(n3, classification));
      assertFalse(db.matchesClassification(n2, classification));
      assertFalse(db.matchesClassification(n, classification));
    }
  }

  private TaxonNameNode create(Origin origin, String sciname, Rank rank, TaxonomicStatus status, boolean isRoot) {
    return create(origin, sciname, sciname, rank, status, isRoot, null, null);
  }

  private TaxonNameNode create(Origin origin, String sciname, String canonical, Rank rank, TaxonomicStatus status, boolean isRoot, @Nullable String taxonID, @Nullable String remark) {
    Name n = new Name();
    n.setScientificName(sciname);
    //n.setCanonicalName(canonical);
    n.setRank(rank);

    Taxon t = new Taxon();
    //t.setOrigin(origin);
    t.setStatus(status);
    t.setKey(taxonID);
    //t.setRemarks(remark);

    return create(t, n, isRoot);
  }

  private TaxonNameNode create(String taxonID, String canonical, String sciname) {
    return create(Origin.SOURCE, sciname, canonical, null, null, false, taxonID, null);
  }

  private TaxonNameNode create(Taxon t, Name n, boolean isRoot) {
    Node node = db.createTaxon();
    if (t.getStatus() != null && t.getStatus().isSynonym()) {
      node.addLabel(Labels.SYNONYM);
    }
    if (isRoot) {
      node.addLabel(Labels.ROOT);
    }
    TaxonNameNode tnn = new TaxonNameNode(node, n, t);
    db.create(tnn);
    return tnn;
  }

  public static TaxonNameNode usage(int key) {
    return usage(key, Rank.SPECIES);
  }

  public static TaxonNameNode usage(int key, Rank rank) {
    Name n = new Name();
    n.setScientificName("Abies alba Mill.");
    //n.setCanonicalName("Abies alba");
    n.setRank(rank);

    Taxon t = new Taxon();
    t.setKey(String.valueOf(key));
    //t.setKingdomKey(key);
    //t.setParentKey(key);
    //t.setAcceptedKey(key);
    t.setStatus(TaxonomicStatus.ACCEPTED);
    TaxonNameNode u = new TaxonNameNode(n, t);
    return u;
  }

}