package life.catalogue.importer;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.neo.model.NeoName;
import life.catalogue.importer.neo.model.NeoUsage;
import life.catalogue.importer.neo.model.RelType;
import life.catalogue.matching.NameIndexFactory;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.validation.Validation;
import javax.validation.Validator;

import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.Iterators;

import de.undercouch.citeproc.csl.CSLType;

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
        } else if (n.getName().getScientificName().equals("Viridae")){
          assertEquals(1, v.getIssues().size());
          assertTrue(v.hasIssue(Issue.RANK_NAME_SUFFIX_CONFLICT));
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
        assertNotNull(r.getId());
        assertNotNull(r.getCitation());
        if (r.getId().equals("greene1895")) {
          assertNull(r.getCsl().getTitle());
          assertNotNull(r.getCsl().getContainerTitle());
        } else if (r.getId().equals("rel")) {
          assertNull(r.getCsl().getTitle());
          assertNull(r.getCsl().getContainerTitle());
        } else {
          assertNotNull(r.getCsl().getTitle());
        }
        if (r.getCsl().getType() == CSLType.ARTICLE_JOURNAL) {
          assertNotNull(r.getCsl().getContainerTitle());
        }
      });

      t = usageByNameID("1001c");
      assertFalse(t.isSynonym());
      assertEquals("1001c", t.getId());

      assertEquals(3, store.typeMaterial().size());

      t = usageByID("10");
      assertEquals(2, t.estimates.size());
    }
  }

  @Test
  public void infragenerics() throws Exception {
    normalize(31);

    try (Transaction tx = store.getNeo().beginTx()) {
      store.usages().allIds().forEach( id -> {
        var u = store.usageWithName(id);
        var n = u.getNeoName().getName();
        System.out.println("\n" + u.getId());
        System.out.println(n);
        if (u.getId().startsWith("10")) {
          assertEquals(Rank.GENUS, n.getRank());
          assertEquals("Abies", n.getScientificName());
          assertEquals("Mill.", n.getAuthorship());
          assertEquals("Abies", n.getUninomial());
          assertEquals(Authorship.authors("Mill."), n.getCombinationAuthorship());
          assertNull(n.getGenus());
          assertNull(n.getInfragenericEpithet());

        } else if (u.getId().startsWith("11")) {
          assertEquals(Rank.SUBGENUS, n.getRank());
          assertEquals("Abies (Ferox)", n.getScientificName());
          assertEquals("Mill.", n.getAuthorship());
          assertEquals("Abies", n.getGenus());
          assertEquals("Ferox", n.getInfragenericEpithet());
          assertEquals(Authorship.authors("Mill."), n.getCombinationAuthorship());
          assertNull(n.getUninomial());

        } else if (u.getId().startsWith("12")) {
          assertEquals(Rank.SUBGENUS, n.getRank());
          assertEquals("Ferox", n.getScientificName());
          assertEquals("Mill.", n.getAuthorship());
          assertNull(n.getGenus());
          assertEquals("Ferox", n.getInfragenericEpithet());
          assertEquals(Authorship.authors("Mill."), n.getCombinationAuthorship());
          assertNull(n.getUninomial());

        } else if (u.getId().startsWith("13")) {
          assertEquals(Rank.SECTION_BOTANY, n.getRank());
          assertEquals("Abies sect. Ferox", n.getScientificName());
          assertEquals("Mill.", n.getAuthorship());
          assertEquals("Abies", n.getGenus());
          assertEquals("Ferox", n.getInfragenericEpithet());
          assertEquals(Authorship.authors("Mill."), n.getCombinationAuthorship());
          assertNull(n.getUninomial());

        } else if (u.getId().startsWith("14")) {
          assertEquals(Rank.SECTION_BOTANY, n.getRank());
          assertEquals("sect. Ferox", n.getScientificName());
          assertEquals("Mill.", n.getAuthorship());
          assertNull(n.getGenus());
          assertEquals("Ferox", n.getInfragenericEpithet());
          assertEquals(Authorship.authors("Mill."), n.getCombinationAuthorship());
          assertNull(n.getUninomial());

        } else if (u.usage.getOrigin() == Origin.DENORMED_CLASSIFICATION) {
          // ignore
        } else {
          throw new IllegalStateException("Should not have usages with origin="+u.usage.getOrigin()+" and ID="+u.getId());
        }
      });
    }
  }

  @Test
  public void testWscRefs() throws Exception {
    normalize(23);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("urn:lsid:nmbe.ch:spidersp:000002");
      assertFalse(t.isSynonym());
      assertEquals("Heptathela amamiensis Haupt, 1983", t.usage.getName().getLabel());
      assertEquals("1938232063", t.usage.getName().getPublishedInId());
      assertEquals("283", t.usage.getName().getPublishedInPage());

      var r = refByID("1938232063");
      assertEquals("1938232063", r.getId());
      assertFalse(r.getCitation().startsWith("(n.d.)"));
    }
  }

  @Test
  @Ignore("work in progress")
  public void testExcelMites() throws Exception {
    normalizeExcel("Torotrogla_villosa.xlsx", NomCode.ZOOLOGICAL);
    store.dump();
    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1922");
      assertFalse(t.isSynonym());
      assertEquals("Picobia villosa Hancock, 1895", t.usage.getName().getLabel());
      assertEquals(1895, (int) t.usage.getName().getPublishedInYear());
      assertEquals("384", t.usage.getName().getPublishedInPage());
      assertEquals("Angaben zu locality, deposition und host entnommen 97000593", t.usage.getRemarks());
      assertEquals("Angaben zu locality, deposition und host entnommen 97000593", t.usage.getRemarks());


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
        assertNotNull(r.getId());
        assertNotNull(r.getCitation());
        if (r.getId().equals("greene1895")) {
          assertNull(r.getCsl().getTitle());
          assertNotNull(r.getCsl().getContainerTitle());
        } else if (r.getId().equals("rel")) {
          assertNull(r.getCsl().getTitle());
          assertNull(r.getCsl().getContainerTitle());
        } else {
          assertNotNull(r.getCsl().getTitle());
        }
        if (r.getCsl().getType() == CSLType.ARTICLE_JOURNAL) {
          assertNotNull(r.getCsl().getContainerTitle());
        }
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
    assertEquals("http://purl.obolibrary.org/obo/NOMEN_0000383", n.getName().getRemarks());
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

        } else if (u.getId().equals("2")){
          assertEquals(1, v.getIssues().size());
          assertTrue(v.hasIssue(Issue.RANK_NAME_SUFFIX_CONFLICT));

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
      Taxon tt = t.asTaxon();
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
        if (vr.getType() == ColdpTerm.NameRelation) {
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
  }

  /**
   * https://github.com/CatalogueOfLife/testing/issues/141
   */
  @Test
  public void zooSection() throws Exception {
    normalize(12);

    printTree();
  }

  @Test
  public void bareNameUsages() throws Exception {
    normalize(15);

    try (Transaction tx = store.getNeo().beginTx()) {
      //125,,Species,bare name,,Jupunba bara,L.,,,,,
      NeoName nn = nameByID("125");
      assertEquals("Jupunba bara", nn.getName().getScientificName());
      assertEquals("L.", nn.getName().getAuthorship());
      var nu = usageByID("125");
      assertNull(nu);

      //124,,Species,unplaced,unavailable,Jupunba nuduta,L.,,,,nom.nud.,
      nn = nameByID("124");
      assertEquals("Jupunba nuduta", nn.getName().getScientificName());
      assertEquals("L.", nn.getName().getAuthorship());
      assertEquals("nom.nud.; unavailable", nn.getName().getRemarks());
      assertEquals(NomStatus.NOT_ESTABLISHED, nn.getName().getNomStatus());
      nu = usageByID(nn.getId());
      assertNull(nu);

      // 331502,609287,Species,Synonym,,Jupunba abbottii,(Rose & Leonard) Britton & Rose,R2,1928,R2,nom.illeg.,
      nn = nameByID("331502");
      assertEquals("Jupunba abbottii", nn.getName().getScientificName());
      assertEquals("(Rose & Leonard) Britton & Rose", nn.getName().getAuthorship());
      assertEquals("nom.illeg.", nn.getName().getRemarks());
      assertEquals((Integer)1928, nn.getName().getPublishedInYear());
      assertEquals("R2", nn.getName().getPublishedInId());
      //TODO: derive status from remarks!
      //assertEquals(NomStatus.UNACCEPTABLE, nn.getName().getNomStatus());
      nu = usageByID(nn.getId());
      assertNotNull(nu);
      assertEquals(TaxonomicStatus.SYNONYM, nu.usage.getStatus());

      // test reference
      // R2,Barneby & J.W.Grimes,N. Amer. Fl.,1928,23,,27
      Reference r2 = refByID("R2");
      assertEquals("Barneby, & Grimes, J. W. (1928). N. Amer. Fl., 23, 27.", r2.getCitation());
      assertEquals("Barneby; Grimes,J.W.", CslUtil.toColdpString(r2.getCsl().getAuthor()));
      assertEquals("N. Amer. Fl.", r2.getCsl().getContainerTitle());
      assertEquals("23", r2.getCsl().getVolume());
      assertNull(r2.getCsl().getIssue());
      assertEquals("27", r2.getCsl().getPage());
    }
  }

  /**
   * https://github.com/CatalogueOfLife/testing/issues/177
   * Also mark WCVP synonyms, illegitimate & invalid names with no parent accepted name as “bare names”
   */
  @Test
  public void hybridGeneraWcvp() throws Exception {
    normalize(18);
    printTree();

    try (Transaction tx = store.getNeo().beginTx()) {
      // a synonym with no parent!
      // 704264-az			synonym	Orthographic	Species	Cassine congonha	A.St.-Hil.			orth. var.	R300753
      assertNull(usageByID("704264-az"));
      var nn = nameByID("704264-az");
      printRelations(nn.node);
      var n = nn.getName();
      assertEquals(NomStatus.NOT_ESTABLISHED, n.getNomStatus());
      assertEquals("Cassine congonha", n.getScientificName());
    }
    assertTree();
  }

  @Test
  public void testBatchIserterInterruptCleanup() throws Exception {
    String resourceDir = resourceDir(1, DataFormat.COLDP);
    URL url = getClass().getResource(resourceDir);

    store = NeoDbFactory.create(1, 1, cfg);
    dws = new DatasetWithSettings();
    dws.setKey(store.getDatasetKey());
    dws.setDataFormat(DataFormat.COLDP);
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Normalizer norm = new Normalizer(dws, store, Paths.get(url.toURI()), NameIndexFactory.passThru(), ImageService.passThru(), validator, null);

    var t = new Thread(new FutureTask<>(norm));
    t.start();

    TimeUnit.MILLISECONDS.sleep(100);
    t.interrupt();
    TimeUnit.MILLISECONDS.sleep(500);

    assertFalse(store.isBatchMode());
    System.out.println("Test FINISHED");
  }

  @Test
  public void literatureOnly() throws Exception {
    normalize(32);

    try (Transaction tx = store.getNeo().beginTx()) {
      assertEquals(11, store.references().size());
      assertEquals(0, store.names().size());
      assertEquals(0, store.typeMaterial().size());
    }
  }

  @Test
  public void latlon() throws Exception {
    normalize(33);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoName nn = nameByID("S110070040");
      assertEquals("Caloptilia acerifoliella", nn.getName().getScientificName());
      assertEquals("(Chambers, 1875)", nn.getName().getAuthorship());

      var nu = usageByID("S110070040");
      var v = store.getVerbatim(nu.getVerbatimKey());
      assertFalse(v.getIssues().contains(Issue.LAT_LON_INVALID));

      for (TypeMaterial tm : store.typeMaterial()) {
        v = store.getVerbatim(tm.getVerbatimKey());
        assertFalse(v.getIssues().contains(Issue.LAT_LON_INVALID));
      }
    }
  }

}
