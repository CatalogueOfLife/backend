package life.catalogue.matching;

import life.catalogue.TestConfigs;
import life.catalogue.api.vocab.Users;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.List;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class NidxExportJobIT {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.nidx();

  @Test
  public void export() {
    var cfg = TestConfigs.build();
    var job = new NidxExportJob(List.copyOf(dataRule.testData.datasetKeys), 1, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg.normalizer);
    job.run();
  }
}