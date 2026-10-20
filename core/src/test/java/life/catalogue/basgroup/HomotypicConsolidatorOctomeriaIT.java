package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.common.io.Resources;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.*;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The names around Octomeria colombiana Schltr. (48JZ6) in the COL extended release as the homotypic grouper saw them:
 * its synonyms and all names it is related to, and one level further the synonyms and relations of those names.
 * Base release names keep their base release status, merged names the status of their source,
 * and only the relations that came with the data are loaded, never those the grouper created.
 * Usage ids are the release ids, base release names have priority 1, merged names 100 plus their sector priority.
 * https://github.com/CatalogueOfLife/backend/issues/1600
 */
public class HomotypicConsolidatorOctomeriaIT {
  private static final String DIR = "txtree/homconsolidation-48jz6/";

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
  public void colombiana() throws Exception {
    HomotypicConsolidatorIT.loadRelations(datasetKey, DIR + "relations.tsv");
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? 0 : lnu.getSectorKey()
    );
    hc.consolidate();
    HomotypicConsolidatorIT.assertNoLoop(datasetKey);

    System.out.println("\n*** RELATIONS ***");
    var rels = HomotypicConsolidatorIT.assertGrouperRelations(datasetKey);
    // Schltr. published colombiana in ten genera and the base release lists them all separately, so they are
    // distinct names. The recombinations already carry their basionym relation from the source, which leaves
    // only the two spellings of Trachelosiphon to relate, the base release spelling being the trusted one.
    // Pabst's amazonica and Luer's auriculata alike.
    assertEquals(Set.of(
      "Trachelosiphon columbianum Schltr. SPELLING_CORRECTION Trachelosiphon colombianum Schltr."
    ), rels);

    var issues = issues();
    issues.forEach((label, iss) -> System.out.println(label + " " + iss));
    assertTrue("No issues expected: " + issues, issues.isEmpty());

    // all names come from one source, so nothing is consolidated
    SectorSyncIT.assertTree("homconsolidation-48jz6", datasetKey, null, Resources.stream(DIR + "expected.txtree"));
  }

  /**
   * @return issues by usage label, for all usages having any
   */
  private Map<String, Set<?>> issues() throws IOException {
    Map<String, Set<?>> issues = new TreeMap<>();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      var vsm = session.getMapper(VerbatimSourceMapper.class);
      for (var u : num.processDataset(datasetKey, null, null)) {
        var v = vsm.getByUsage(DSID.of(datasetKey, u.getId()));
        if (v != null && !v.getIssues().isEmpty()) {
          issues.put(u.getLabel(), v.getIssues());
        }
      }
    }
    return issues;
  }
}
