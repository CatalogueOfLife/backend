package life.catalogue.exporter;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Reference;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

  /**
   * Cancelling a running export (here simulated by interrupting the thread mid-iteration)
   * must abort the export via checkIfCancelled(), end as CANCELED and not leave an archive.
   */
  @Test
  public void cancel() {
    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru()) {
      @Override
      void write(NameUsageBase u) {
        super.write(u);
        // interrupt right after the first usage was written; the next usage's
        // checkIfCancelled() in the core loop must pick this up and abort
        Thread.currentThread().interrupt();
      }
    };
    exp.run();
    // run() leaves the interrupt flag set - clear it so it does not leak into other tests
    Thread.interrupted();

    assertEquals(JobStatus.CANCELED, exp.getStatus());
    assertFalse("A cancelled export must not produce an archive", exp.getArchive().exists());
  }

  /**
   * Cancelling during the reference export exercises the per-record check in a Consumer lambda,
   * which aborts via the unchecked InterruptedRuntimeException and is converted back to a checked
   * InterruptedException at the export() boundary - so the job must still end CANCELED, not FAILED.
   */
  @Test
  public void cancelDuringReferences() {
    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru()) {
      @Override
      void write(Reference r) throws IOException {
        super.write(r);
        // interrupt after the first reference; the next one's per-record check must abort
        Thread.currentThread().interrupt();
      }
    };
    exp.run();
    Thread.interrupted();

    assertEquals(JobStatus.CANCELED, exp.getStatus());
    assertFalse("A cancelled export must not produce an archive", exp.getArchive().exists());
  }
}
