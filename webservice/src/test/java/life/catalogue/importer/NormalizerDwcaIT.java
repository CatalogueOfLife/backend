package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.importer.neo.model.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.collection.Iterators;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerDwcaIT extends NormalizerITBase {
  
  
  public NormalizerDwcaIT() {
    super(DataFormat.DWCA);
  }
  

  @Test
  public void testBdjCsv() throws Exception {
    normalize(17);
    
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1099-sp16");
      assertFalse(t.isSynonym());
      assertEquals("Pinus palustris Mill.", t.usage.getName().getLabel());
      assertEquals(URI.create("http://dx.doi.org/10.3897/BDJ.2.e1099"), t.asTaxon().getLink());
    }
  }
  
  @Test
  public void testPublishedIn() throws Exception {
    normalize(0);
    
    for (Reference r : store.references()) {
      System.out.println(r);
    }
    
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage trametes_modesta = usageByID("324805");
      assertFalse(trametes_modesta.isSynonym());

      Reference pubIn = store.references().get(trametes_modesta.usage.getName().getPublishedInId());
      assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getCitation());
      assertNotNull(pubIn.getId());

      NeoUsage Polystictus_substipitatus = usageByID("140283");
      assertTrue(Polystictus_substipitatus.isSynonym());
      assertTrue(Polystictus_substipitatus.asSynonym().getStatus().isSynonym());
      pubIn = store.references().get(Polystictus_substipitatus.usage.getName().getPublishedInId());
      assertEquals("Syll. fung. (Abellini) 21: 318 (1912)", pubIn.getCitation());

      NeoUsage Polyporus_modestus = usageByID("198666");
      assertTrue(Polyporus_modestus.isSynonym());
      assertTrue(Polyporus_modestus.asSynonym().getStatus().isSynonym());
      pubIn = store.references().get(Polyporus_modestus.usage.getName().getPublishedInId());
      assertEquals("Linnaea 5: 519 (1830)", pubIn.getCitation());
    }
  }
  
  @Test
  public void testSupplementary() throws Exception {
    normalize(24);
    
    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // check species name
      NeoUsage t = usageByID("1000");
      assertEquals("Crepis pulchra", t.usage.getName().getScientificName());

      // check vernaculars
      Map<String, String> expV = Maps.newHashMap();
      expV.put("deu", "Schöner Pippau");
      expV.put("eng", "smallflower hawksbeard");
      assertEquals(expV.size(), t.vernacularNames.size());
      for (VernacularName vn : t.vernacularNames) {
        assertEquals(expV.remove(vn.getLanguage()), vn.getName());
      }
      assertTrue(expV.isEmpty());
      
      // check distributions
      List<Distribution> expD = PgImportIT.expectedDwca24Distributions();
      
      assertEquals(expD.size(), t.distributions.size());
      // remove keys before we check equality
      t.distributions.forEach(d -> {
        d.setKey(null);
        d.setVerbatimKey(null);
      });
      Set<Distribution> imported = Sets.newHashSet(t.distributions);
      Set<Distribution> expected = Sets.newHashSet(expD);

      Sets.SetView<Distribution> diff = Sets.difference(expected, imported);
      for (Distribution d : diff) {
        System.out.println(d);
      }
      assertEquals(expected, imported);
    }
  }
  
  @Test
  public void chainedBasionyms() throws Exception {
    normalize(28);

    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // 1->2->1
      // should be: 1->2
      NeoName t1 = nameByID("1");
      NeoName t2 = nameByID("2");

      assertEquals(1, t1.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(1, t2.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(t2.node,
          t1.node.getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING).getEndNode());

      // 10->11->12->10, 13->11
      // should be: 10,13->11 12
      NeoName t10 = nameByID("10");
      NeoName t11 = nameByID("11");
      NeoName t12 = nameByID("12");
      NeoName t13 = nameByID("13");

      assertEquals(1, t10.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(2, t11.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(0, t12.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(1, t13.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(t11.node, t10.node
          .getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING).getOtherNode(t10.node));
      assertEquals(t11.node, t13.node
          .getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING).getOtherNode(t13.node));
    }
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/69
   */
  @Test
  public void testIcznLists() throws Exception {
    normalize(26);
    
    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // check species name
      NeoUsage t = usageByID("10156");
      assertEquals("'Prosthète'", t.usage.getName().getScientificName());
    }
  }
  
  @Test
  public void testNeoIndices() throws Exception {
    normalize(1);
    
    // no indices!
    try (Transaction tx = store.getNeo().beginTx()) {
      Schema schema = store.getNeo().schema();
      assertFalse(schema.getIndexes().iterator().hasNext());

      // 1001, Crepis bakeri Greene
      assertNotNull(Iterators.singleOrNull(
          store.getNeo().findNodes(Labels.NAME, NeoProperties.SCIENTIFIC_NAME, "Crepis bakeri")
      ));
  
      assertNull(Iterators.singleOrNull(
          store.getNeo().findNodes(Labels.NAME, NeoProperties.SCIENTIFIC_NAME, "xCrepis bakeri")
      ));
    }
  }
  
  @Test
  public void testIdRels() throws Exception {
    normalize(1);
    
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u1 = usageByID("1006");
      NeoUsage u2 = byName("Leontodon taraxacoides", "(Vill.) Mérat");

      assertEquals(u1, u2);

      NeoUsage bas = byName("Leonida taraxacoida");

      NeoUsage syn = byName("Leontodon leysseri");
      assertTrue(syn.asSynonym().getStatus().isSynonym());
    }
  }

  @Test
  public void testProParte() throws Exception {
    normalize(8);
    
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage syn = usageByID("1001");
      assertNotNull(syn.asSynonym());

      Map<String, String> expectedAccepted = Maps.newHashMap();
      expectedAccepted.put("1000", "Calendula arvensis");
      expectedAccepted.put("10000", "Calendula incana subsp. incana");
      expectedAccepted.put("10002", "Calendula incana subsp. maderensis");

      for (RankedUsage acc : store.accepted(syn.node)) {
        assertEquals(expectedAccepted.remove(store.names().objByNode(acc.nameNode).getId()), acc.name);
      }
      assertTrue(expectedAccepted.isEmpty());
    }
  }
  
  @Test
  public void testHomotypic() throws Exception {
    normalize(29);
    
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage annua1 = usageByID("4");
      NeoUsage annua2 = usageByID("5");
      NeoUsage reptans1 = usageByID("7");
      NeoUsage reptans2 = usageByID("8");
    }
  }
  
  @Test
  public void testNameRelations() throws Exception {
    normalize(30);
    
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t10 = usageByID("10");
      NeoUsage t11 = usageByID("11");

      NeoName nn = nameByID("10");
      List<NameRelation> rels = store.nameRelations(nn.node);
      assertEquals(1, rels.size());
      assertEquals(NomRelType.BASED_ON, rels.get(0).getType());
    }
  }
  
  @Test
  public void testIssueFlagging() throws Exception {
    normalize(31);
    debug();
    try (Transaction tx = store.getNeo().beginTx()) {
      VerbatimRecord t9 = vByUsageID("9");
      VerbatimRecord v9 = vByNameID("9");
      assertTrue(t9.hasIssue(Issue.PUBLISHED_BEFORE_GENUS));
      assertFalse(t9.hasIssue(Issue.PARENT_NAME_MISMATCH));

      VerbatimRecord t11 = vByUsageID("11");
      assertTrue(t11.hasIssue(Issue.PARENT_NAME_MISMATCH));

      VerbatimRecord t103 = vByUsageID("103");
      assertFalse(t103.hasIssue(Issue.PUBLISHED_BEFORE_GENUS));
      assertFalse(t103.hasIssue(Issue.PARENT_NAME_MISMATCH));

      VerbatimRecord t104 = vByUsageID("104");
      assertTrue(t104.hasIssue(Issue.PUBLISHED_BEFORE_GENUS));
    }
  }
  
  @Test
  @Ignore("No testing yet")
  public void testWormsParents() throws Exception {
    normalize(32);

    try (Transaction tx = store.getNeo().beginTx()) {
    }
  }
  
  @Test
  public void dwc8Nons() throws Exception {
    normalize(34);
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u;
      for (Node n : Iterators.loop(store.getNeo().findNodes(Labels.USAGE))) {
        u = store.usageWithName(n);
        if (u.usage.getName().getOrigin() == Origin.SOURCE) {
          System.out.println(u.usage.getStatus() + ": " + u.usage.getName().getLabel());
          System.out.println("  " + u.usage.getName().getRemarks());
          System.out.println("  " + u.usage.getAccordingToId());
          assertNotNull(u.usage.getAccordingToId());
        }
      }

      // 8	Phylata	Anthurium lanceum Engl., nom. illeg., non. A. lancea.	Markus
      u = usageByID("8");
      assertEquals("Anthurium lanceum", u.usage.getName().getScientificName());
      assertEquals("Engl. nom.illeg.", u.usage.getName().getAuthorship());
      assertEquals("nom.illeg.", u.usage.getName().getNomenclaturalNote());
      assertNull(u.usage.getName().getRemarks());

      assertEquals("non. A.lancea.", u.usage.getNamePhrase());
      assertNull(u.usage.getName().getRemarks());
      assertNotNull(u.usage.getAccordingToId());
      Reference sec = accordingTo(u.usage);
      assertEquals("Markus", sec.getCitation());
      assertEquals(NomStatus.UNACCEPTABLE, u.usage.getName().getNomStatus());
      //assertTrue(store.getVerbatim(u.usage.getName().getVerbatimKey()).hasIssue(Issue.PARTIALLY_PARSABLE_NAME));
    }
  }
  
  @Test
  @Ignore
  public void testExternal() throws Exception {

    //normalize(URI.create("https://svampe.databasen.org/dwc/dwcchecklistarchive.zip"));
    normalize(URI.create("http://sftp.kew.org/pub/data_collaborations/Fabaceae/DwCA/wcvp_fabaceae_DwCA.zip"));
    //normalize(URI.create("http://www.marinespecies.org/dwca/WoRMS_DwC-A.zip"));
    //normalize(Paths.get("/Users/markus/code/col+/data-world-plants/dwca"));
    //normalize(URI.create("https://raw.githubusercontent.com/mdoering/ion-taxonomic-hierarchy/master/classification.tsv"));
    // print("Diversity", GraphFormat.TEXT, false);
  }

}
