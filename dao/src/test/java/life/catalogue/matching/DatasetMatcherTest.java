package life.catalogue.matching;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

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
    NameIndex nidx = NameIndexFactory.build(NamesIndexConfig.memory(512), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.createWithoutAuthormap()).started();
    // we only have one verbatim record. If we dont insert into the names index this will be a no match with an issue
    DatasetMatcher m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    m.match(11, false, false);

    // again, now also insert new names into the index
    m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    m.match(11, true, false);

    // dont update issues
    m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    m.match(11, true, false);
  }
}