package life.catalogue.matching;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class DatasetMatcherTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void rematchApple() throws Exception {
    NameIndex nidx = NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), AuthorshipNormalizer.createWithoutAuthormap()).started();
    // we only have one verbatim record. If we dont insert into the names index this will be a no match with an issue
    DatasetMatcher m = new DatasetMatcher(PgSetupRule.getSqlSessionFactory(), nidx, true);
    m.match(11, false);

    // again, now also insert new names into the index
    m = new DatasetMatcher(PgSetupRule.getSqlSessionFactory(), nidx, true);
    m.match(11, true);

    // dont update issues
    m = new DatasetMatcher(PgSetupRule.getSqlSessionFactory(), nidx, false);
    m.match(11, true);
  }
}