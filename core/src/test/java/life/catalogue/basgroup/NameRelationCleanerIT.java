package life.catalogue.basgroup;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Relations as WoRMS delivers them: https://github.com/CatalogueOfLife/data/issues/1815
 * <ul>
 *   <li>Tilapia sparrmanii with its misspellings related to it both as basionym and as spelling correction,
 *   and a misspelled recombination in Chromis related both ways too. One spelling correction is given twice.</li>
 *   <li>A spelling correction from Coryphaena hippurus to the misspelling of its synonym C. scomberoides.</li>
 *   <li>A misspelling only related by its basionym stays as it is.</li>
 *   <li>A genus related to its type species also as its basionym.</li>
 *   <li>A replacement name with a redundant homotypic relation, a later homonym also said to be homotypic
 *   and a later homonym relation given in both directions.</li>
 *   <li>A recombination the data relates as later homonym, which the grouper must not relate as basionym.</li>
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
    assertEquals(12, new NameRelationCleaner(factory, datasetKey).clean());

    assertEquals(Set.of(
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparmani Smith, 1840",
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparmanii Smith, 1840",
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparmanni Smith, 1840",
      "Tilapia sparrmanii Smith, 1840 SPELLING_CORRECTION Tilapia sparrmanni Smith, 1840",
      "Chromis sparrmanii (Smith, 1840) BASIONYM Tilapia sparrmanii Smith, 1840",
      "Chromis sparrmanni (Smith, 1840) BASIONYM Tilapia sparrmanii Smith, 1840",
      "Coryphaena scomberoides Valenciennes, 1833 SPELLING_CORRECTION Coryphaena scomberoïdes Valenciennes, 1833",
      "Coriphaena hippurus Linnaeus, 1758 BASIONYM Coryphaena hippurus Linnaeus, 1758",
      "Hydradendrium Carter, 1880 TYPE Hydradendrium spinosum Carter, 1880",
      "Neuquenina Rosas Costa, 1959 REPLACEMENT_NAME Neuquenia Rehn, 1943",
      "Eupholus Guérin-Méneville, 1838 LATER_HOMONYM Eupholus Boisduval, 1835",
      "Termes destructor Smeathman, 1781 LATER_HOMONYM Termes destructor De Geer, 1778",
      "Bus bus (Smith, 1850) LATER_HOMONYM Aus bus Smith, 1850"
    ), relations());

    // the usages of both names explain why a relation is gone
    assertIssues("tsp1", Issue.NAME_RELATION_CONFLICT);
    assertIssues("spar", Issue.NAME_RELATION_CONFLICT, Issue.SPELLING_CORRECTION_IMPLAUSIBLE);
    assertIssues("hyds", Issue.NAME_RELATION_CONFLICT);
    assertIssues("neq", Issue.NAME_RELATION_REDUNDANT);
    assertIssues("eup2", Issue.NAME_RELATION_CONFLICT);
    assertIssues("tdes2", Issue.NAME_RELATION_CONFLICT);
    assertIssues("cori");

    // nothing left to clean
    assertEquals(0, new NameRelationCleaner(factory, datasetKey).clean());

    // the data relates all names already or calls them homonyms, so the grouper must not add any relation
    HomotypicConsolidator.entireDataset(factory, datasetKey,
      lnu -> lnu.getSectorKey() == null ? Integer.MAX_VALUE : lnu.getSectorKey()
    ).consolidate();
    HomotypicConsolidatorIT.assertNoLoop(datasetKey);
    assertEquals(Set.of(), HomotypicConsolidatorIT.assertGrouperRelations(datasetKey));
  }

  /**
   * Limited to the Cichlidae only the Tilapia relations are cleaned, all other families stay as they are.
   */
  @Test
  public void cleanSubtree() throws Exception {
    HomotypicConsolidatorIT.loadRelations(datasetKey, DIR + "relations.tsv");
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    var before = relations();
    // 4 basionyms of misspellings, the spelling correction of another genus and a duplicate
    assertEquals(6, new NameRelationCleaner(factory, datasetKey, "cich").clean());

    var after = relations();
    assertFalse(after.contains("Tilapia sparmani Smith, 1840 BASIONYM Tilapia sparrmanii Smith, 1840"));
    assertTrue(after.contains("Chromis sparrmanni (Smith, 1840) BASIONYM Tilapia sparrmanii Smith, 1840"));
    before.removeIf(r -> r.contains("Tilapia") || r.contains("Chromis"));
    after.removeIf(r -> r.contains("Tilapia") || r.contains("Chromis"));
    assertEquals(before, after);
    assertIssues("tsp1", Issue.NAME_RELATION_CONFLICT);
    assertIssues("neq");
  }

  @Test
  public void jobs() throws Exception {
    var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    final int user = TestDataRule.TEST_USER.getKey();
    assertEquals(new HomotypicConsolidationJob.ConsolidationParams(datasetKey, null, true),
      new HomotypicConsolidationJob(factory, datasetKey, user).getParams());
    assertEquals(new HomotypicConsolidationJob.ConsolidationParams(datasetKey, "cich", false),
      new HomotypicConsolidationJob(factory, datasetKey, user, "cich", false).getParams());
    assertEquals(new NameRelationCleanerJob.CleanerParams(datasetKey, "cich"),
      new NameRelationCleanerJob(factory, datasetKey, user, "cich").getParams());
    // the root must be an existing accepted name
    assertThrows(NotFoundException.class, () -> new NameRelationCleanerJob(factory, datasetKey, user, "nope"));
    assertThrows(IllegalArgumentException.class, () -> new NameRelationCleanerJob(factory, datasetKey, user, "tsp1"));
  }

  private void assertIssues(String usageID, Issue... expected) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var v = session.getMapper(VerbatimSourceMapper.class).getByUsage(DSID.of(datasetKey, usageID));
      Set<Issue> issues = v == null ? Set.of() : v.getIssues();
      Set<Issue> relIssues = issues.stream()
        .filter(i -> i == Issue.NAME_RELATION_CONFLICT || i == Issue.NAME_RELATION_REDUNDANT || i == Issue.SPELLING_CORRECTION_IMPLAUSIBLE)
        .collect(Collectors.toSet());
      assertEquals(usageID, Set.of(expected), relIssues);
    }
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
