package life.catalogue.importer;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Issue;
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


import static org.junit.Assert.assertEquals;
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
    store.names().all().forEach(n -> {
      VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
      assertNotNull(v);
    });

    // run again to get proper matches!
    store.close();
    normalize(0);
    store.names().all().forEach(n -> {
      assertNotNull(n.getName().getNamesIndexId());
      VerbatimRecord v = store.getVerbatim(n.getVerbatimKey());
      assertNotNull(v);
    });
  }

  /**
   * Accepted names that share their canonical name and rank under the same code, regardless of authorship:
   * https://github.com/CatalogueOfLife/backend/issues/1218
   */
  @Test
  public void duplicateAcceptedNames() throws Exception {
    normalize(48);
    // different authors, same code
    assertFlagged("s1", Issue.DUPLICATE_ACCEPTED_NAME, true);
    assertFlagged("s2", Issue.DUPLICATE_ACCEPTED_NAME, true);
    // the same name as a synonym
    assertFlagged("s3", Issue.DUPLICATE_ACCEPTED_NAME, false);
    assertFlagged("s4", Issue.DUPLICATE_ACCEPTED_NAME, false);
    // homonyms under different codes
    assertFlagged("g1", Issue.DUPLICATE_ACCEPTED_NAME, false);
    assertFlagged("g2", Issue.DUPLICATE_ACCEPTED_NAME, false);
    // a missing code is inferred or compatible with any
    assertFlagged("g3", Issue.DUPLICATE_ACCEPTED_NAME, true);
    assertFlagged("g4", Issue.DUPLICATE_ACCEPTED_NAME, true);
    assertFlagged("v2", Issue.DUPLICATE_ACCEPTED_NAME, false);
  }

  /**
   * Homotypic names whose usages share no accepted taxon:
   * https://github.com/CatalogueOfLife/backend/issues/1158
   */
  @Test
  public void homotypicNamesOutsideSynonymy() throws Exception {
    normalize(48);
    // basionym accepted as another taxon
    assertFlagged("v7", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    assertFlagged("v2", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    // basionym of a synonym of another taxon
    assertFlagged("v6", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    assertFlagged("v4", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    // homotypic synonym of its own taxon
    assertFlagged("v3", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, false);
    assertFlagged("v5", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, false);
  }

  private void assertFlagged(String usageID, Issue issue, boolean flagged) {
    boolean has = vByUsageID(usageID).contains(issue);
    assertEquals(issue + (flagged ? " missing from " : " unexpected on ") + usageID, flagged, has);
  }

}
