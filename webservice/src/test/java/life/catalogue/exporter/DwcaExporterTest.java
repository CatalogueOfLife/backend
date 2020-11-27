package life.catalogue.exporter;

import com.google.common.io.Files;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class DwcaExporterTest {

  File dir;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();

  @Before
  public void initCfg()  {
    dir = Files.createTempDir();
  }

  @After
  public void cleanup()  {
    FileUtils.deleteQuietly(dir);
  }

  @Test
  public void dataset() {
    DwcaExporter exp = DwcaExporter.dataset(TestDataRule.APPLE.key, Users.TESTER, PgSetupRule.getSqlSessionFactory(), dir);
    exp.run();

    assertTrue(exp.getArchive().exists());
  }
}