package life.catalogue.exporter;

import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.img.ImageService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ColdpExporterTest extends ExporterTest {
  ExportRequest req;

  @Before
  public void initReq()  {
    req = ExportRequest.dataset(TestDataRule.APPLE.key, Users.TESTER);
  }

  @Test
  public void dataset() {
    ColdpExporter exp = new ColdpExporter(req, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertTrue(exp.getArchive().exists());
  }

  @Test
  public void excel() {
    req.setExcel(true);
    ColdpExporter exp = new ColdpExporter(req, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertTrue(exp.getArchive().exists());
  }
}
