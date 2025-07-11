package life.catalogue.exporter;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

public class ColdpExtendedExportIT extends ExportTest {
  ExportRequest req;

  @Before
  public void initReq()  {
    req = new ExportRequest(TestDataRule.APPLE.key, DataFormat.COLDP);
    req.setExtended(true);
    // add CSL data to refs to test CSL export
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
      rm.processDataset(TestDataRule.APPLE.key).forEach(r -> {
        var csl = r.getCsl();
        if (csl == null) {
          csl = new CslData();
          csl.setTitle("Das Kapital " + r.getId());
          csl.setAuthor(new CslName[]{new CslName("Karl", "Marx"), new CslName("Friedrich", "Engels")});
          csl.setIssued(new CslDate(1867));
          r.setCsl(csl);
          rm.update(r);
        }
      });
    }
  }

  @Test
  public void dataset() {
    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertExportExists(exp.getArchive());
  }

  @Test
  public void bareName() {
    req.setBareNames(true);
    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertExportExists(exp.getArchive());
  }

  @Test
  public void excel() {
    req.setExcel(true);
    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();

    assertExportExists(exp.getArchive());
  }
}
