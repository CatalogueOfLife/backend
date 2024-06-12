package life.catalogue.matching;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class RematchMissingTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void run() {
    NameIndex nidx = NameIndexFactory.memory(NamesIndexConfig.memory(512), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.createWithoutAuthormap()).started();
    DatasetMatcher m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    var task = new RematchMissing(m, testDataRule.testData.key);
    task.run();
  }
}