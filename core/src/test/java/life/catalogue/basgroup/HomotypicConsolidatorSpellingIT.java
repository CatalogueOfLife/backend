package life.catalogue.basgroup;

import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.common.io.Resources;
import life.catalogue.junit.*;

import java.util.Set;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

/**
 * Misspellings and spelling variants in homotypic groups: https://github.com/CatalogueOfLife/backend/issues/1662
 * <ul>
 *   <li>Coryphaena hippurus as WoRMS delivers it: its misspellings with a basionym relation to it and a spelling correction.
 *   The data relates them all already, so the grouper must neither pick a misspelling as the original nor add relations.</li>
 *   <li>A name with the status of an orthographic variant is never the original name.</li>
 *   <li>A genus misspelled by one letter in another source is an orthographic variant.</li>
 *   <li>Names only differing by their subgenus are duplicates and get no relation.</li>
 * </ul>
 */
public class HomotypicConsolidatorSpellingIT {
  private static final String DIR = "txtree/homconsolidation-1662/";

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
  public void misspellings() throws Exception {
    HomotypicConsolidatorIT.loadRelations(datasetKey, DIR + "relations.tsv");
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? Integer.MAX_VALUE : lnu.getSectorKey()
    );
    hc.consolidate();
    HomotypicConsolidatorIT.assertNoLoop(datasetKey);

    var rels = HomotypicConsolidatorIT.assertGrouperRelations(datasetKey);
    assertEquals(Set.of(
      "Coryphaena equiselis Linnaeus, 1758 SPELLING_CORRECTION Coryphaena equisalis Linnaeus, 1758",
      "Coryphaena imperialis Rafinesque, 1810 SPELLING_CORRECTION Coryphena imperialis Rafinesque, 1810"
    ), rels);

    SectorSyncIT.assertTree("homconsolidation-1662", datasetKey, null, Resources.stream(DIR + "expected.txtree"));
  }
}
