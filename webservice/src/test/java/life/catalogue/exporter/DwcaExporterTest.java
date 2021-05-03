package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.img.ImageService;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DwcaExporterTest extends ExporterTest {

  @Test
  public void dataset() {
    DwcaExporter exp = new DwcaExporter(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, PgSetupRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertTrue(exp.getArchive().exists());
  }

}