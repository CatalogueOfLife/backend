package life.catalogue.matching;

import life.catalogue.api.vocab.Users;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DatasetMatcherTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void rematchApple() throws Exception {
    NameIndex nidx = NameIndexFactory.build(NamesIndexConfig.memory(512), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.createWithoutAuthormap()).started();
    // all 5 apple names are matched already. Rename one so it no longer matches anything in the names index.
    // The test data is parsed on load, so the name parts must change too
    execute("UPDATE name SET scientific_name = 'Aus bus', genus = 'Aus', specific_epithet = 'bus' WHERE dataset_key = 11 AND id = 'name-4'");

    // If we dont insert into the names index the renamed name loses its match
    DatasetMatcher m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    m.match(11, false, false, Users.TESTER);
    // a name without a match has no match record at all, never one with an empty index id
    assertEquals(0, countMatches("index_id IS NULL"));
    assertEquals(4, countMatches("index_id IS NOT NULL"));

    // again, now also insert new names into the index
    m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    m.match(11, true, false, Users.TESTER);
    assertEquals(0, countMatches("index_id IS NULL"));
    assertEquals(5, countMatches("index_id IS NOT NULL"));

    // dont update issues
    m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    m.match(11, true, false, Users.TESTER);
  }

  private static void execute(String sql) throws SQLException {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         Statement st = session.getConnection().createStatement()
    ) {
      st.execute(sql);
    }
  }

  private static int countMatches(String condition) throws SQLException {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
         Statement st = session.getConnection().createStatement();
         ResultSet rs = st.executeQuery("SELECT count(*) FROM name_match WHERE dataset_key=11 AND " + condition)
    ) {
      rs.next();
      return rs.getInt(1);
    }
  }
}
