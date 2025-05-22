package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.junit.Test;

public class DwcaExtendedExportTest extends ExportTest {

  @Test
  public void dataset() {
    DwcaExtendedExport exp = new DwcaExtendedExport(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertExportExists(exp.getArchive());
  }

  @Test
  public void withBareNames() {
    var req = new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA);
    req.setBareNames(true);
    DwcaExtendedExport exp = new DwcaExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertExportExists(exp.getArchive());
  }

}