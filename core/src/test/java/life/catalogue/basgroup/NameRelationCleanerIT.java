package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.junit.*;

import java.util.HashSet;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

/**
 * Relations as WoRMS delivers them: https://github.com/CatalogueOfLife/data/issues/1815
 * <ul>
 *   <li>Tilapia sparrmanii with its misspellings related to it both as basionym and as spelling correction,
 *   and a misspelled recombination in Chromis related both ways too.</li>
 *   <li>A spelling correction from Coryphaena hippurus to the misspelling of its synonym C. scomberoides.</li>
 *   <li>A misspelling only related by its basionym stays as it is.</li>
 * </ul>
 */
public class NameRelationCleanerIT {
  private static final String DIR = "txtree/relconflicts-1815/";

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
  public void clean() throws Exception {
    HomotypicConsolidatorIT.loadRelations(datasetKey, DIR + "relations.tsv");
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    assertEquals(7, new NameRelationCleaner(factory, datasetKey).clean());

    assertEquals(Set.of(
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparmani Smith, 1840",
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparmanii Smith, 1840",
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparmanni Smith, 1840",
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparrmanni Smith, 1840",
      "Chromis sparrmanii (Smith, 1840) BASIONYM Tilapia sparrmanii Smith, 1840",
      "Chromis sparrmanni (Smith, 1840) BASIONYM Tilapia sparrmanii Smith, 1840",
      "Coryphaena scomberoides Valenciennes, 1833 SPELLING_CORRECTION Coryphaena scomberoïdes Valenciennes, 1833",
      "Coriphaena hippurus Linnaeus, 1758 BASIONYM Coryphaena hippurus Linnaeus, 1758"
    ), relations());

    // nothing left to clean
    assertEquals(0, new NameRelationCleaner(factory, datasetKey).clean());

    // the data relates all names already, so the grouper must not add any
    HomotypicConsolidator.entireDataset(factory, datasetKey,
      lnu -> lnu.getSectorKey() == null ? Integer.MAX_VALUE : lnu.getSectorKey()
    ).consolidate();
    HomotypicConsolidatorIT.assertNoLoop(datasetKey);
    assertEquals(Set.of(), HomotypicConsolidatorIT.assertGrouperRelations(datasetKey));
  }

  private Set<String> relations() {
    Set<String> rels = new HashSet<>();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var nm = session.getMapper(NameMapper.class);
      for (NameRelation nr : session.getMapper(NameRelationMapper.class).processDataset(datasetKey)) {
        var n1 = nm.get(DSID.of(datasetKey, nr.getNameId()));
        var n2 = nm.get(DSID.of(datasetKey, nr.getRelatedNameId()));
        rels.add(n1.getLabel() + " " + nr.getType() + " " + n2.getLabel());
      }
    }
    return rels;
  }
}
