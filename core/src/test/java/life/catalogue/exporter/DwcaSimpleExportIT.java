package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.junit.Test;

public class DwcaSimpleExportIT extends ExportTest {

  @Test
  public void dataset() {
    var exp = DwcaSimpleExport.build(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertExportExists(exp.getArchive());
  }
}