package life.catalogue.exporter;

import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ColdpExporterTest extends ExporterTest {

  @Test
  public void dataset() {
    ColdpExporter exp = ColdpExporter.dataset(TestDataRule.APPLE.key, Users.TESTER, PgSetupRule.getSqlSessionFactory(), dir);
    exp.run();

    assertTrue(exp.getArchive().exists());
  }
}
