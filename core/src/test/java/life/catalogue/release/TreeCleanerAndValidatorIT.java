package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Distribution;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.ThreatStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.api.vocab.area.GenericArea;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.basgroup.HomotypicConsolidatorIT;
import life.catalogue.db.mapper.DistributionMapper;
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
  final static int rulesKey = 102;

  public final static SqlSessionFactoryRule pg = new PgSetupRule(); // PgConnectionRule("col", "postgres", "postgres");

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pg)
    .around(TestDataRule.empty())
    .around(new TxtTreeDataRule(List.of(
      new TxtTreeDataRule.TreeDataset(datasetKey, "txtree/validation/mismatch.txtree"),
      new TxtTreeDataRule.TreeDataset(synParentKey, "txtree/validation/synonym-parent.txtree"),
      new TxtTreeDataRule.TreeDataset(rulesKey, "txtree/validation/rules.txtree")
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

  /**
   * Flags derived from the tree and from whole dataset queries:
   * extinct inconsistencies (#1349), homotypic names outside the synonymy (#1158)
   * and ambiguous red list status (#1480).
   */
  @Test
  public void datasetRules() throws Exception {
    HomotypicConsolidatorIT.loadRelations(rulesKey, "txtree/validation/rules-relations.tsv");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         var st = session.getConnection().createStatement()
    ) {
      // text trees only state extinct taxa, so explicitly extant ones are set here
      st.execute("UPDATE name_usage SET extinct = false WHERE dataset_key = " + rulesKey + " AND id IN ('agno','pisi','cho')");
      var dm = session.getMapper(DistributionMapper.class);
      addDistribution(dm, "cracca", "Global", ThreatStatus.CRITICALLY_ENDANGERED);
      addDistribution(dm, "cracca", "Global", ThreatStatus.LEAST_CONCERN);
      addDistribution(dm, "cracca", "Germany", ThreatStatus.VULNERABLE);
      // same status twice and different areas are no conflict
      addDistribution(dm, "sativa", "Global", ThreatStatus.LEAST_CONCERN);
      addDistribution(dm, "sativa", "Global", ThreatStatus.LEAST_CONCERN);
      addDistribution(dm, "sativa", "Germany", ThreatStatus.ENDANGERED);
      addDistribution(dm, "faba", "Global", ThreatStatus.LEAST_CONCERN);
      addDistribution(dm, "faba", "Global", ThreatStatus.NOT_EVALUATED);
    }

    validate(rulesKey);

    // extant below an extinct taxon
    assertFlagged(rulesKey, "agno", Issue.EXTINCT_INCONSISTENT, true);
    assertFlagged(rulesKey, "pisi", Issue.EXTINCT_INCONSISTENT, true);
    // unknown extinct flag and extant taxa without an extinct parent
    assertFlagged(rulesKey, "page", Issue.EXTINCT_INCONSISTENT, false);
    assertFlagged(rulesKey, "boot", Issue.EXTINCT_INCONSISTENT, false);
    assertFlagged(rulesKey, "cho", Issue.EXTINCT_INCONSISTENT, false);
    assertFlagged(rulesKey, "agn", Issue.EXTINCT_INCONSISTENT, false);

    // basionym accepted as another taxon
    assertFlagged(rulesKey, "fsat", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    assertFlagged(rulesKey, "faba", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    // basionym is a synonym of another taxon
    assertFlagged(rulesKey, "angu", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    assertFlagged(rulesKey, "cracca", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, true);
    // homotypic synonym of its own taxon
    assertFlagged(rulesKey, "fabav", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, false);
    assertFlagged(rulesKey, "sativa", Issue.HOMOTYPIC_NAME_OUTSIDE_SYNONYMY, false);

    assertFlagged(rulesKey, "cracca", Issue.REDLIST_STATUS_AMBIGUOUS, true);
    assertFlagged(rulesKey, "sativa", Issue.REDLIST_STATUS_AMBIGUOUS, false);
    assertFlagged(rulesKey, "faba", Issue.REDLIST_STATUS_AMBIGUOUS, false);
  }

  private static void addDistribution(DistributionMapper dm, String taxonID, String area, ThreatStatus status) {
    var d = new Distribution();
    d.setDatasetKey(rulesKey);
    d.setArea(new GenericArea(area));
    d.setThreatStatus(status);
    d.applyUser(Users.TESTER);
    dm.create(d, taxonID);
  }

  void assertFlagged(int key, String usageID, Issue issue, boolean flagged) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var v = session.getMapper(VerbatimSourceMapper.class).getByUsage(DSID.of(key, usageID));
      boolean has = v != null && v.getIssues().contains(issue);
      assertEquals(issue + (flagged ? " missing from " : " unexpected on ") + usageID, flagged, has);
    }
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
