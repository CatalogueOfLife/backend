package life.catalogue.basgroup;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.*;
import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Many consolidation tests in one text tree file.
 * See homconsolidation.md markdown file for expectations.
 */
public class HomotypicConsolidatorIT {
  @ClassRule
  public final static PgSetupRule pg = new PgSetupRule();

  final int datasetKey = 100;
  final TestDataRule dataRule = TestDataRule.empty();
  final NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(dataRule)
    .around(new TxtTreeDataRule(datasetKey, "txtree/homconsolidation.txtree")) // loads prio values into sector keys - lower is better
    .around(matchingRule);

  /**
   * Superfamily Bdelloidea: https://github.com/CatalogueOfLife/data/issues/1465
   *
   * @throws IOException
   */
  @Test
  public void homconsolidation() throws IOException {
    var hc = HomotypicConsolidator.entireDataset(SqlSessionFactoryRule.getSqlSessionFactory(), datasetKey,
      lnu -> lnu.getSectorKey() == null ? Integer.MAX_VALUE : lnu.getSectorKey()
    );
    hc.consolidate();
    assertNoLoop(datasetKey);
    assertGrouperRelations(datasetKey);
    SectorSyncIT.assertTree("homconsolidation-expected.txtree", datasetKey, null, getClass().getResourceAsStream("/txtree/homconsolidation-expected.txtree"));
  }

  /**
   * Asserts the grouper claims spelling corrections only for orthographic variants and never relates duplicates,
   * i.e. the same name and rank.
   *
   * @return all relations the grouper created, rendered as "label TYPE label"
   */
  public static Set<String> assertGrouperRelations(int datasetKey) {
    Set<String> rels = new HashSet<>();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var nm = session.getMapper(NameMapper.class);
      for (NameRelation nr : session.getMapper(NameRelationMapper.class).processDataset(datasetKey)) {
        if (Objects.equals(nr.getCreatedBy(), Users.HOMOTYPIC_GROUPER)) {
          var n1 = nm.get(DSID.of(datasetKey, nr.getNameId()));
          var n2 = nm.get(DSID.of(datasetKey, nr.getRelatedNameId()));
          var rel = n1.getLabel() + " " + nr.getType() + " " + n2.getLabel();
          System.out.println(rel);
          if (nr.getType() == NomRelType.SPELLING_CORRECTION) {
            assertTrue("Spelling correction between other names: " + rel, HomotypicConsolidator.isOrthographicVariant(n1, n2));
          }
          if (n1.getRank() == n2.getRank()) {
            assertNotEquals("Duplicate names related: " + rel, n1.getScientificName().toLowerCase(), n2.getScientificName().toLowerCase());
          }
          rels.add(rel);
        }
      }
    }
    return rels;
  }

  public static void assertNoLoop(int datasetKey) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      var cycles = session.getMapper(NameUsageMapper.class).detectLoop(datasetKey);
      if (cycles != null && !cycles.isEmpty()) {
        cycles.forEach(id -> System.out.println(id));
        throw new IllegalStateException("Loops in classification at id=" + cycles.get(0));
      }
    }
  }
}