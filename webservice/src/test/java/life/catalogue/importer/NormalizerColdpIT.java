package life.catalogue.importer;

import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.Iterators;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerColdpIT extends NormalizerITBase {
  
  public NormalizerColdpIT() {
    super(DataFormat.COLDP);
  }
  
  @Test
  public void testSpecs() throws Exception {
    normalize(0);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1000");
      assertFalse(t.isSynonym());
      assertEquals("Platycarpha glomerata (Thunberg) A. P. de Candolle", t.usage.getName().getLabel());
  
      t = usageByNameID("1006-s3");
      assertTrue(t.isSynonym());
      assertEquals("1006-1006-s3", t.getId());
      assertEquals("1006-s3", t.usage.getName().getId());
      assertEquals("Leonida taraxacoida Vill.", t.usage.getName().getLabel());
  
      List<NameRelation> rels = store.nameRelations(t.nameNode);
      assertEquals(1, rels.size());
      assertEquals(NomRelType.BASIONYM, rels.get(0).getType());
  
      t = accepted(t.node);
      assertFalse(t.isSynonym());
      assertEquals("1006", t.getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", t.usage.getName().getLabel());
      
      parents(t.node, "102", "30", "20", "10", "1");

      store.names().all().forEach(n -> {
        VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
        assertNotNull(v);
        if (n.getName().getId().equals("cult")){
          assertEquals(1, v.getIssues().size());
          assertTrue(v.hasIssue(Issue.INCONSISTENT_NAME));
        } else if (n.getName().getId().equals("fake")){
          assertEquals(1, v.getIssues().size());
          assertTrue(v.hasIssue(Issue.PARENT_SPECIES_MISSING));
        } else {
          assertEquals(0, v.getIssues().size());
        }
      });

      store.usages().all().forEach(u -> {
        VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
        assertNotNull(v);
        if (u.getId().equals("fake")) {
          assertEquals(1, v.getIssues().size());
          assertTrue(v.hasIssue(Issue.PARTIAL_DATE));
        } else {
          assertTrue(v.getIssues().isEmpty());
        }
      });
  
      store.references().forEach(r -> {
        assertNotNull(r.getCitation());
        assertNotNull(r.getCsl().getTitle());
      });

      t = usageByNameID("1001c");
      assertFalse(t.isSynonym());
      assertEquals("1001c", t.getId());

      assertEquals(3, store.typeMaterial().size());

      t = usageByID("10");
      assertEquals(2, t.estimates.size());
    }
  }

  /**
   * https://github.com/Sp2000/colplus-backend/issues/307
   */
  @Test
  public void testSelfRels() throws Exception {
    normalize(2);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("10");
      assertFalse(t.isSynonym());
      assertTrue(synonyms(t.node).isEmpty());
      
      t = usageByID("11");
      assertFalse(t.isSynonym());
      synonyms(t.node, "12");
    }
  }
  
  /**
   * https://github.com/Sp2000/colplus-backend/issues/433
   */
  @Test
  public void testNullRel() throws Exception {
    normalize(3);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("13");
      assertFalse(t.isSynonym());
      assertTrue(synonyms(t.node).isEmpty());
      assertBasionym(t, null);
  
      t = usageByID("11");
      assertFalse(t.isSynonym());
      assertBasionym(t, "12");
    }
  }

  @Test
  public void aspilota() throws Exception {
    // before we run this we configure the name parser to do better
    // then we check that it really worked and no issues get attached
    ParserConfigDao.addToParser(NormalizerTxtTreeIT.aspilotaCfg());

    normalize(5);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage u = usageByID("1");
      assertFalse(u.isSynonym());
      assertEquals("Aspilota vector Belokobylskij, 2007", u.usage.getName().getLabel());
      assertEquals(NameType.SCIENTIFIC, u.usage.getName().getType());
      assertEquals("Aspilota", u.usage.getName().getGenus());
      assertEquals("vector", u.usage.getName().getSpecificEpithet());

      VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
      assertTrue(v.getIssues().isEmpty());
    }
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/760
   *
   * And testing uninomial vs scientificName as sole name input
   */
  @Test
  public void nomStatus() throws Exception {
    normalize(6);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      assertRosanae("846548");
      assertRosanae("846548b");
      assertRosanae("846548c");
    }
  }

  private void assertRosanae(String id){
    NeoName n = nameByID(id);
    assertEquals("Rosanae", n.getName().getLabel());
    assertEquals("Rosanae", n.getName().getUninomial());
    assertEquals("Rosanae", n.getName().getScientificName());
    assertEquals(NameType.SCIENTIFIC, n.getName().getType());
    assertEquals(Rank.SUPERORDER, n.getName().getRank());
    assertNull(n.getName().getNomenclaturalNote());
    assertNull(n.getName().getUnparsed());
    assertNull(n.getName().getGenus());
    assertNull(n.getName().getAuthorship());
  }

  @Test
  public void testSpecsNameUsage() throws Exception {
    normalize(7);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1000");
      assertFalse(t.isSynonym());
      assertEquals("Platycarpha glomerata (Thunberg) A. P. de Candolle", t.usage.getName().getLabel());

      t = usageByID("1006-s3");
      assertTrue(t.isSynonym());
      assertEquals("1006-s3", t.getId());
      assertEquals("1006-s3", t.usage.getName().getId());
      assertEquals("Leonida taraxacoida Vill.", t.usage.getName().getLabel());

      List<NameRelation> rels = store.nameRelations(t.nameNode);
      assertEquals(1, rels.size()); // 2 redundant basionym relations (originalNameID & NameRel) should become just one!
      assertEquals(NomRelType.BASIONYM, rels.get(0).getType());

      t = accepted(t.node);
      assertFalse(t.isSynonym());
      assertEquals("1006", t.getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", t.usage.getName().getLabel());

      parents(t.node, "102", "30", "20", "10", "1");

      store.usages().all().forEach(u -> {
        VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
        assertNotNull(v);
        if (u.getId().equals("fake")) {
          assertEquals(2, v.getIssues().size());
          assertTrue(v.hasIssue(Issue.PARTIAL_DATE));
          assertTrue(v.hasIssue(Issue.PARENT_SPECIES_MISSING));

        } else if(u.getId().equals("cult")) {
          assertEquals(1, v.getIssues().size());
          assertTrue(v.hasIssue(Issue.INCONSISTENT_NAME));

        } else {
          assertEquals(0, v.getIssues().size());
        }
      });

      store.references().forEach(r -> {
        assertNotNull(r.getCitation());
        assertNotNull(r.getCsl().getTitle());
      });

      t = usageByNameID("1001c");
      assertFalse(t.isSynonym());
      assertEquals("1001c", t.getId());

      assertEquals(3, store.typeMaterial().size());
    }
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/841
   */
  @Test
  public void misapplied() throws Exception {
    normalize(8);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1225-3");
      assertTrue(t.isSynonym());
      assertEquals(TaxonomicStatus.MISAPPLIED, t.usage.getStatus());
      assertEquals("auct. nec Zeller, 1877", t.usage.getNamePhrase());
      assertEquals("Platyptilia fuscicornis", t.usage.getName().getLabel());
      assertEquals("Platyptilia fuscicornis auct. nec Zeller, 1877", t.usage.getLabel());

      t = usageByID("778");
      assertFalse(t.isSynonym());
      assertEquals(TaxonomicStatus.ACCEPTED, t.usage.getStatus());
      Taxon tt = t.getTaxon();
      assertTrue(tt.isExtinct());
      assertNull(tt.getNamePhrase());
      assertEquals("†Anstenoptilia marmarodactyla Dyar, 1902", tt.getLabel());
    }
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/941
   */
  @Test
  public void basionymIdEqualsTaxonID() throws Exception {
    normalize(9);

    final String key = "urn:lsid:marinespecies.org:taxname:1252865";
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoName nn = nameByID(key);
      assertEquals("Paludina longispira", nn.getName().getScientificName());
      assertEquals("E.A. Smith, 1886", nn.getName().getAuthorship());

      VerbatimRecord v = vByNameID(key);
      assertTrue(v.getIssues().isEmpty());

      List<Relationship> rels = Iterators.asList( nn.node.getRelationships(RelType.HAS_BASIONYM).iterator() );
      assertEquals(1, rels.size());

      for (VerbatimRecord vr : store.verbatimList()) {
        if (vr.getType()== ColdpTerm.NameRelation) {
          if (vr.getRaw(ColdpTerm.nameID).equals(key)) {
            assertTrue(vr.hasIssue(Issue.SELF_REFERENCED_RELATION));
          } else {
            assertTrue(vr.getIssues().isEmpty());
          }
        }
      }
    }
  }

  @Test
  public void excelFabaceae() throws Exception {
    normalize(10);

    final String key = "331502";
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoName nn = nameByID(key);
      assertEquals("Jupunba abbottii", nn.getName().getScientificName());
      assertEquals("(Rose & Leonard) Britton & Rose", nn.getName().getAuthorship());

      var nu = usageByID(key);
      assertTrue(nu.isSynonym());
      List<Relationship> rels = Iterators.asList( nu.node.getRelationships().iterator() );
      assertEquals(2, rels.size());
      rels.removeIf(r -> !r.getType().name().equals(RelType.SYNONYM_OF.name()));
      assertEquals(1, rels.size());
    }
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1035
   */
  @Test
  public void badlyNestedRanks() throws Exception {
    normalize(11);

    try (Transaction tx = store.getNeo().beginTx()) {
      for (String id : List.of("3","4","7")) {
        var t = usageByID(id);
        assertTrue("Missing issue for "+id, hasIssues(t, Issue.CLASSIFICATION_RANK_ORDER_INVALID));
      }
      for (String id : List.of("1","2","8a","8","9","10","11","12","13","14")) {
        var t = usageByID(id);
        assertFalse("Wrong issue for "+id, hasIssues(t, Issue.CLASSIFICATION_RANK_ORDER_INVALID));
      }
    }
  }}
