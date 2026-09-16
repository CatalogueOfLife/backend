package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.junit.TxtTreeDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

public class TreeCleanerAndValidatorIT {

  final static int datasetKey = 100;
  final static int synParentKey = 101;

  public final static SqlSessionFactoryRule pg = new PgSetupRule(); // PgConnectionRule("col", "postgres", "postgres");

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pg)
    .around(TestDataRule.empty())
    .around(new TxtTreeDataRule(List.of(
      new TxtTreeDataRule.TreeDataset(datasetKey, "txtree/validation/mismatch.txtree"),
      new TxtTreeDataRule.TreeDataset(synParentKey, "txtree/validation/synonym-parent.txtree")
    )));

  static void validate(int key) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      new TreeCleanerAndValidator(session, key, false).validate(session);
    }
  }

  @Test
  public void parentMismatch() throws IOException {
    validate(datasetKey);

    // start tests
    assertIssues(Rank.SPECIES, "Diamesa aberrata");
    assertIssues(Rank.SPECIES, "Burundi negeriana", Issue.PARENT_NAME_MISMATCH);
    assertIssues(Rank.SPECIES, "Diamessa kundera", Issue.PARENT_NAME_MISMATCH);
    assertIssues(Rank.SPECIES, "Nesodiamesa negeriana", Issue.PARENT_NAME_MISMATCH);
    assertIssues(Rank.SPECIES, "Ablabesmyia suturalis", Issue.PARENT_GENUS_MISSING);
    assertIssues(Rank.SPECIES, "Ablabesmyia satanis", Issue.PARENT_GENUS_MISSING);

    assertIssues(Rank.SUBSPECIES, "Diamesa vulgaris vulgaris", Issue.PARENT_SPECIES_MISSING);

    assertIssues(Rank.SUBGENUS, "Nesodiamesa", Issue.SYNONYM_RANK_DIFFERS);
    assertIssues(Rank.GENUS, "Onychodiamesa");

    assertIssues(Rank.ORDER, "Heminoptera", Issue.CLASSIFICATION_RANK_ORDER_INVALID, Issue.MISSING_AUTHORSHIP);
    assertIssues(Rank.ORDER, "Hymenoidales", Issue.CLASSIFICATION_RANK_ORDER_INVALID, Issue.NO_SPECIES_INCLUDED, Issue.MISSING_AUTHORSHIP);

    assertIssues(Rank.ORDER, "Hymenoptera", Issue.NO_SPECIES_INCLUDED, Issue.MISSING_AUTHORSHIP);
    assertIssues(Rank.UNRANKED, "Hymenoidies", Issue.MISSING_AUTHORSHIP); // we don not flag unranked taxa
    assertIssues(Rank.SUBORDER, "Hymenoidaloides", Issue.NO_SPECIES_INCLUDED, Issue.MISSING_AUTHORSHIP);
    assertIssues(Rank.FAMILY, "Hymenoidaloidea", Issue.NO_SPECIES_INCLUDED, Issue.RANK_NAME_SUFFIX_CONFLICT, Issue.MISSING_AUTHORSHIP);
    assertIssues(Rank.UNRANKED, "Hymenoidalododes", Issue.MISSING_AUTHORSHIP);
    assertIssues(Rank.SUBFAMILY, "Hymenoidaloidiea", Issue.NO_SPECIES_INCLUDED, Issue.MISSING_AUTHORSHIP);
  }

  /**
   * An accepted taxon whose parent is a synonym, as merges and homotypic grouping leave behind in extended releases,
   * must not abort the validation. It used to throw "Usage parent ... not found" from the parent stack, which the
   * xrelease swallowed, leaving every usage after it unvalidated.
   */
  @Test
  public void taxonBelowSynonym() throws Exception {
    var kundera = SectorSyncIT.getByName(synParentKey, Rank.SPECIES, "Diamesa kundera");
    var syn = SectorSyncIT.getByName(synParentKey, Rank.GENUS, "Onychodiamesa");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(NameUsageMapper.class).updateParentId(DSID.of(synParentKey, kundera.getId()), syn.getId(), Users.TESTER);
    }

    validate(synParentKey);

    // both roots are validated, whichever is traversed first
    assertIssues(synParentKey, Rank.KINGDOM, "Animalia", Issue.MISSING_AUTHORSHIP);
    assertIssues(synParentKey, Rank.SPECIES, "Diamesa aberrata");
    assertIssues(synParentKey, Rank.SPECIES, "Poa annua", Issue.MISSING_AUTHORSHIP);
    // the taxon below the synonym is left out
    assertIssues(synParentKey, Rank.SPECIES, "Diamesa kundera");
  }

  void assertIssues(Rank rank, String name, Issue ... issues) {
    assertIssues(datasetKey, rank, name, issues);
  }

  void assertIssues(int key, Rank rank, String name, Issue ... issues) {
    var u = SectorSyncIT.getByName(key, rank, name);
    assertNotNull(u);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var vm = session.getMapper(VerbatimSourceMapper.class);
      var v = vm.getByUsage(DSID.of(key, u.getId()));
      if (issues == null || issues.length == 0) {
        assertFalse(v != null && v.hasIssues());
      } else {
        assertEquals(issues.length, v.getIssues().size());
        for (var iss : issues) {
          assertTrue("Issue "+iss+" missing from "+name, v.getIssues().contains(iss));
        }
      }
    }
  }

}
