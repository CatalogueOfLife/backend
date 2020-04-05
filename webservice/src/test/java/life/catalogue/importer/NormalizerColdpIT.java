package life.catalogue.importer;

import life.catalogue.api.model.NameRelation;
import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.dao.ParserConfigDao;
import life.catalogue.importer.neo.model.NeoUsage;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

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
      assertEquals("Platycarpha glomerata (Thunberg) A.P.de Candolle", t.usage.getName().canonicalNameWithAuthorship());
  
      t = usageByNameID("1006-s3");
      assertTrue(t.isSynonym());
      assertEquals("1006-1006-s3", t.getId());
      assertEquals("1006-s3", t.usage.getName().getId());
      assertEquals("Leonida taraxacoida Vill.", t.usage.getName().canonicalNameWithAuthorship());
  
      List<NameRelation> rels = store.relations(t.nameNode);
      assertEquals(1, rels.size());
      assertEquals(NomRelType.BASIONYM, rels.get(0).getType());
  
      t = accepted(t.node);
      assertFalse(t.isSynonym());
      assertEquals("1006", t.getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", t.usage.getName().canonicalNameWithAuthorship());
      
      parents(t.node, "102", "30", "20", "10", "1");
      
      store.names().all().forEach(n -> {
        VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
        assertNotNull(v);
        assertEquals(1, v.getIssues().size());
        assertTrue(v.hasIssue(Issue.NAME_MATCH_NONE));
      });
  
      store.usages().all().forEach(u -> {
        VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
        assertNotNull(v);
        assertTrue(v.getIssues().isEmpty());
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
      assertEquals("Aspilota vector Belokobylskij, 2007", u.usage.getName().canonicalNameWithAuthorship());
      assertEquals(NameType.SCIENTIFIC, u.usage.getName().getType());
      assertEquals("Aspilota", u.usage.getName().getGenus());
      assertEquals("vector", u.usage.getName().getSpecificEpithet());

      VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
      assertTrue(v.getIssues().isEmpty());
    }
  }
}
