package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.vocab.Issue;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.TestDataRule;

import java.sql.SQLException;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.TransactionState;

import static org.junit.Assert.*;

public class IssueAdderTest extends DaoTestBase {
  static final int KEY = TestDataRule.DRAFT.key;

  public IssueAdderTest() {
    super(new TestDataRule(TestDataRule.DRAFT));
  }

  /**
   * Creating an adder must not open a transaction. The xrelease builds one on a non autocommit session and then
   * waits for a tree query that can outlast the idle-in-transaction timeout of the server, see #1605.
   */
  @Test
  public void noTransactionBeforeFirstIssue() throws SQLException {
    try (SqlSession s = factory().openSession(false)) {
      var adder = new IssueAdder(KEY, s);
      assertEquals(TransactionState.IDLE, s.getConnection().unwrap(BaseConnection.class).getTransactionState());

      adder.addIssue("c1", Issue.NO_SPECIES_INCLUDED);
      adder.addIssue("c2", Issue.NO_SPECIES_INCLUDED);
      s.commit();

      VerbatimSourceMapper vsm = s.getMapper(VerbatimSourceMapper.class);
      for (var id : Set.of("c1", "c2")) {
        var vsKey = vsm.getVSKeyByUsage(DSID.of(KEY, id));
        assertNotNull(vsKey);
        assertTrue(vsm.getIssues(DSID.of(KEY, vsKey)).getIssues().contains(Issue.NO_SPECIES_INCLUDED));
      }
      assertNotEquals(vsm.getVSKeyByUsage(DSID.of(KEY, "c1")), vsm.getVSKeyByUsage(DSID.of(KEY, "c2")));
    }
  }
}
