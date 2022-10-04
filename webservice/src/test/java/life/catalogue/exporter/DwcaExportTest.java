package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.img.ImageService;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DwcaExportTest extends ExportTest {

  @Test
  public void dataset() {
    DwcaExport exp = new DwcaExport(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru(), timer);
    exp.run();

    assertTrue(exp.getArchive().exists());
  }

  @Test
  public void withBareNames() {
    var req = new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA);
    req.setBareNames(true);
    DwcaExport exp = new DwcaExport(req, Users.TESTER, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru(), timer);
    exp.run();

    assertTrue(exp.getArchive().exists());
  }

}