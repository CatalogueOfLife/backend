package org.col.admin.importer;

import org.col.admin.importer.neo.model.NeoUsage;
import org.col.api.vocab.DataFormat;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NormalizerColdpIT extends NormalizerITBase {
  
  public NormalizerColdpIT() {
    super(DataFormat.COLDP);
  }
  
  @Test
  public void testSpecs() throws Exception {
    normalize(1);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoUsage t = usageByID("1000");
      assertFalse(t.isSynonym());
      assertEquals("Platycarpha glomerata (Thunberg) A.P.de Candolle", t.usage.getName().canonicalNameComplete());
  
      t = usageByNameID("1006-s3");
      assertTrue(t.isSynonym());
      assertEquals("Leonida taraxacoida Vill.", t.usage.getName().canonicalNameComplete());
    }
  }

}
