package life.catalogue.release;

import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.*;

public class StableIdProviderTest {
  StableIdProvider provider;

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.project();

  @Before
  public void init() {
    provider = new StableIdProvider(TestDataRule.TestData.PROJECT.key,3, PgSetupRule.getSqlSessionFactory());
  }

  @Test
  public void run() throws Exception {
    provider.run();
  }

}