package org.col.importer;

import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.Issue;
import org.col.common.tax.AuthorshipNormalizer;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.col.matching.NameIndex;
import org.col.matching.NameIndexFactory;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.Transaction;

import static org.junit.Assert.*;

/**
 * Normalizer that uses a real name index to match.
 * The index need postgres unfortunately...
 */
public class NormalizerFullIT extends NormalizerITBase {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  public NormalizerFullIT() {
    super(DataFormat.COLDP, NormalizerFullIT::newIndex);
  }
  
  static NameIndex newIndex() {
    return NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), aNormalizer);
  }
  
  @Test
  public void testSpecs() throws Exception {
    // run once to feed the names index
    normalize(0);
    try (Transaction tx = store.getNeo().beginTx()) {
      store.names().all().forEach(n -> {
        VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
        assertNotNull(v);
        assertTrue(v.hasIssue(Issue.NAME_MATCH_INSERTED));
      });
    }
  
    // run again to get proper matches!
    store.closeAndDelete();
    normalize(0);
    try (Transaction tx = store.getNeo().beginTx()) {
      store.names().all().forEach(n -> {
        assertNotNull(n.name.getNameIndexId());

        VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
        assertNotNull(v);
        assertFalse(v.hasIssue(Issue.NAME_MATCH_NONE));
        assertFalse(v.hasIssue(Issue.NAME_MATCH_INSERTED));
      });
    }
  }
 
}
