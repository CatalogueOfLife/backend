package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.*;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The misspelling pass runs after the homotypic pass on the same taxon group and only looks at bi- and trinomials.
 * Cephaloleia fulcipes from a lower priority sector is a misspelling of Cephaloleia sulcipes the orthographic variant
 * detection misses, as it only compares epithets with the same initial. Its subspecies has to follow it to the
 * correctly spelled species. The genus Silvilagus is a uninomial and never consolidated as a misspelling of
 * Sylvilagus, while its species already became a synonym in the homotypic pass, which used to leave the misspelling
 * pass without its usage map.
 * https://github.com/CatalogueOfLife/backend/issues/1633
 * https://github.com/CatalogueOfLife/backend/wiki/Orthographic-Variants
 */
public class HomotypicConsolidatorMisspellingIT {
  private static final String DIR = "txtree/misspelling-consolidation/";

  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final int datasetKey = 100;
  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(datasetKey, DIR + "tree.txtree"))
    .around(matchingRule);

  @Test
  public void misspellingWithChild() throws Exception {
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? 0 : lnu.getSectorKey()
    );
    hc.consolidateMisspellings();
    hc.consolidate();
    HomotypicConsolidatorIT.assertNoLoop(datasetKey);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      var vsm = session.getMapper(VerbatimSourceMapper.class);

      // homotypic pass
      assertEquals(TaxonomicStatus.ACCEPTED, num.get(DSID.of(datasetKey, "ful")).getStatus());
      assertEquals(TaxonomicStatus.SYNONYM, num.get(DSID.of(datasetKey, "ful2")).getStatus());
      // uninomials are never consolidated as misspellings
      assertEquals(TaxonomicStatus.ACCEPTED, num.get(DSID.of(datasetKey, "sil")).getStatus());

      // misspelling pass
      assertEquals(TaxonomicStatus.ACCEPTED, num.get(DSID.of(datasetKey, "sul")).getStatus());
      var misspelt = num.get(DSID.of(datasetKey, "ful3"));
      assertEquals(TaxonomicStatus.SYNONYM, misspelt.getStatus());
      assertEquals("sul", misspelt.getParentId());
      var v = vsm.getByUsage(DSID.of(datasetKey, "ful3"));
      assertTrue(v.getIssues().contains(Issue.MISSPELLING_CONSOLIDATION));
      // the subspecies must not stay below a synonym
      assertEquals("sul", num.get(DSID.of(datasetKey, "fulmin")).getParentId());
    }
  }
}
