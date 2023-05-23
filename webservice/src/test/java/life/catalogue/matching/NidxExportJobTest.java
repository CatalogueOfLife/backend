package life.catalogue.matching;

import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.TempFile;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NidxExportJobTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  @Override
  public BackgroundJob buildJob() {
    return new NidxExportJob(List.copyOf(dataRule.testData.datasetKeys), 1, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), new WsServerConfig());
  }
}