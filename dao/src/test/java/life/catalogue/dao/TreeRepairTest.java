package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.VerbatimSource;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.TestDataRule;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TreeRepairTest extends DaoTestBase {
  static final int KEY = TestDataRule.DRAFT.key;

  public TreeRepairTest() {
    super(new TestDataRule(TestDataRule.DRAFT));
  }

  /**
   * p2 has the accepted children c1 and c2, p3 has c3 which we turn into a synonym. Deleting both parents leaves
   * all three pointing at rows that no longer exist.
   */
  @Before
  public void dropParents() {
    try (SqlSession s = factory().openSession(true)) {
      NameUsageMapper num = s.getMapper(NameUsageMapper.class);
      num.updateStatus(DSID.of(KEY, "c3"), TaxonomicStatus.SYNONYM, Users.TESTER);
      num.delete(DSID.of(KEY, "p2"));
      num.delete(DSID.of(KEY, "p3"));
    }
  }

  @Test
  public void fixMissingParents() {
    List<String> fixed;
    try (SqlSession s = factory().openSession(false)) {
      fixed = TreeRepair.fixMissingParents(s, KEY, null, Users.TESTER);
      s.commit();
    }
    assertEquals(Set.of("c1", "c2", "c3"), Set.copyOf(fixed));

    try (SqlSession s = factory().openSession(true)) {
      NameUsageMapper num = s.getMapper(NameUsageMapper.class);
      assertNull(num.get(DSID.of(KEY, "c1")).getParentId());
      assertNull(num.get(DSID.of(KEY, "c3")).getParentId());
      assertTrue("the status is kept", num.get(DSID.of(KEY, "c3")).getStatus().isSynonym());
      assertEquals(Set.of(Issue.PARENT_ID_INVALID), issues(s, "c1"));
      assertEquals(Set.of(Issue.ACCEPTED_ID_INVALID), issues(s, "c3"));
      // untouched
      assertEquals("k1", num.get(DSID.of(KEY, "p1")).getParentId());
      assertEquals(Set.of(), issues(s, "p1"));

      assertTrue("nothing is left to repair", TreeRepair.fixMissingParents(s, KEY, null, Users.TESTER).isEmpty());
    }
  }

  @Test
  public void fixMissingParentsWithFallback() {
    try (SqlSession s = factory().openSession(false)) {
      TreeRepair.fixMissingParents(s, KEY, "b", Users.TESTER);
      s.commit();
    }
    try (SqlSession s = factory().openSession(true)) {
      NameUsageMapper num = s.getMapper(NameUsageMapper.class);
      assertEquals("b", num.get(DSID.of(KEY, "c1")).getParentId());
      assertEquals("b", num.get(DSID.of(KEY, "c3")).getParentId());
    }
  }

  private static Set<Issue> issues(SqlSession s, String usageId) {
    VerbatimSourceMapper vsm = s.getMapper(VerbatimSourceMapper.class);
    Integer vsKey = vsm.getVSKeyByUsage(DSID.of(KEY, usageId));
    if (vsKey == null) return Set.of();
    VerbatimSource v = vsm.getIssues(DSID.of(KEY, vsKey));
    return v == null || v.getIssues() == null ? Set.of() : Set.copyOf(v.getIssues());
  }
}
