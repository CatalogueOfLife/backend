package life.catalogue.exporter;

import com.google.common.io.Files;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;

import java.io.File;

public class ExporterTest {

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

}