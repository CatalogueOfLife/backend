package life.catalogue.matching;

import com.github.dockerjava.api.DockerClient;

import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.Resources;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class TaxonomicAlignJobTest extends EmailNotificationTemplateTest {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();
  DockerClient docker;

  @Before
  public void init() {
    docker = PgSetupRule.getDockerClient();
  }

  @Override
  public BackgroundJob buildJob() {
    try {
      return new TaxonomicAlignJob(Users.TESTER, dataRule.testData.key, "root-1", dataRule.testData.key, "root-2",
        SqlSessionFactoryRule.getSqlSessionFactory(), docker, new NormalizerConfig()
      );
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void alignment() throws Exception {
    var job = buildJob();
    job.run();
    assertTrue(job.isFinished());
  }

  class TaxonomicAlignJobTestData extends TaxonomicAlignJob {
    public TaxonomicAlignJobTestData(int userKey, int datasetKey1, String root1, int datasetKey2, String root2) throws IOException {
      super(userKey, datasetKey1, root1, datasetKey2, root2, SqlSessionFactoryRule.getSqlSessionFactory(), docker, new NormalizerConfig());
    }

    @Override
    protected void copyData() throws IOException {
      FileUtils.copyDirectory(Resources.toFile("taxalign/a"), src1);
      FileUtils.copyDirectory(Resources.toFile("taxalign/b"), src2);
    }
  }

  @Test
  public void alignmentFixedData() throws Exception {
    var job = new TaxonomicAlignJobTestData(Users.TESTER, dataRule.testData.key, null, dataRule.testData.key, null);
    job.run();
    assertTrue(job.isFinished());
  }
}