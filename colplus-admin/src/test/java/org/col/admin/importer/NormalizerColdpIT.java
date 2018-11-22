package org.col.admin.importer;

import java.util.List;

import org.col.admin.importer.neo.model.NeoUsage;
import org.col.api.model.NameRelation;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.NomRelType;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

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

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1000");
      assertFalse(t.isSynonym());
      assertEquals("Platycarpha glomerata (Thunberg) A.P.de Candolle", t.usage.getName().canonicalNameComplete());
  
      t = usageByNameID("1006-s3");
      assertTrue(t.isSynonym());
      assertEquals(".neodb.Bjwe", t.getId());
      assertEquals("1006-s3", t.usage.getName().getId());
      assertEquals("Leonida taraxacoida Vill.", t.usage.getName().canonicalNameComplete());
  
      List<NameRelation> rels = store.relations(t.nameNode);
      assertEquals(1, rels.size());
      assertEquals(NomRelType.BASIONYM, rels.get(0).getType());
  
      t = accepted(t.node);
      assertFalse(t.isSynonym());
      assertEquals("1006", t.getId());
      assertEquals("Leontodon taraxacoides (Vill.) Mérat", t.usage.getName().canonicalNameComplete());
      
      parents(t.node, "102", "30", "20", "10", "1");
      
    }
  }

}
