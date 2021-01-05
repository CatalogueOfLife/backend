package life.catalogue.exporter;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;

public class DwcaExporterTest extends ExporterTest {

  @Test
  public void dataset() {
    DwcaExporter exp = DwcaExporter.dataset(TestDataRule.APPLE.key, Users.TESTER, PgSetupRule.getSqlSessionFactory(), dir);
    exp.run();

    assertTrue(exp.getArchive().exists());
  }

  @Test
  public void eml() throws Exception {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      File f = File.createTempFile("col-eml", ".xml");
      Dataset d = dm.get(TestDataRule.APPLE.key);
      DwcaExporter.writeEml(d, f);
    }
  }
}