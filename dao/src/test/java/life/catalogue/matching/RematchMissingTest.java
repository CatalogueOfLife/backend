package life.catalogue.matching;

import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;

import life.catalogue.matching.nidx.NamesIndexConfig;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

public class RematchMissingTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void run() throws IOException {
    NameIndex nidx = NameIndexFactory.build(NamesIndexConfig.memory(512), SqlSessionFactoryRule.getSqlSessionFactory(), AuthorshipNormalizer.createWithoutAuthormap()).started();
    DatasetMatcher m = new DatasetMatcher(SqlSessionFactoryRule.getSqlSessionFactory(), nidx, null);
    var task = new RematchMissing(m, testDataRule.testData.key);
    task.run();
  }
}