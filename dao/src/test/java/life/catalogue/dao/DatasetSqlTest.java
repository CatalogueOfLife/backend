package life.catalogue.dao;

import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests the dataset script init routine.
 * Warn: this requires online access to github hosted sql files!
 */
public class DatasetSqlTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.datasets();
  
  @Test
  public void nothing() throws Exception {
    System.out.println("Done");
  }
}

