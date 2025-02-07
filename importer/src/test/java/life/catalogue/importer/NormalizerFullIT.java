package life.catalogue.importer;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;

import life.catalogue.matching.nidx.NamesIndexConfig;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.assertNotNull;

/**
 * Normalizer that uses a real name index to match.
 * The index need postgres unfortunately...
 */
public class NormalizerFullIT extends NormalizerITBase {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  public NormalizerFullIT() {
    super(DataFormat.COLDP, NormalizerFullIT::newIndex);
  }
  
  static NameIndex newIndex() {
    return NameIndexFactory.build(NamesIndexConfig.memory(1024), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.INSTANCE).started();
  }
  
  @Test
  public void testSpecs() throws Exception {
    // run once to feed the names index
    normalize(0);
    try (Transaction tx = store.getNeo().beginTx()) {
      store.names().all().forEach(n -> {
        VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
        assertNotNull(v);
      });
    }
  
    // run again to get proper matches!
    store.closeAndDelete();
    normalize(0);
    try (Transaction tx = store.getNeo().beginTx()) {
      store.names().all().forEach(n -> {
        assertNotNull(n.getName().getNamesIndexType());
        VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
        assertNotNull(v);
      });
    }
  }
 
}
