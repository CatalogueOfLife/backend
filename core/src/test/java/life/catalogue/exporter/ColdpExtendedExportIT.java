package life.catalogue.exporter;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Identifier;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Reference;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.MediaType;
import life.catalogue.api.vocab.Users;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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

  /**
   * The verbatim alternativeID (usage identifiers) and nameAlternativeID (name identifiers) must be
   * populated in the exported NameUsage file, see https://github.com/CatalogueOfLife/checklistbank/issues/1698
   */
  @Test
  public void alternativeIDs() throws Exception {
    // set identifiers on a usage and on its name
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      NameMapper nm = session.getMapper(NameMapper.class);
      num.addIdentifier(DSID.of(TestDataRule.APPLE.key, "root-1"),
        List.of(new Identifier("tsn", "12345"), new Identifier(Identifier.Scope.COL, "ABC")));
      nm.addIdentifier(DSID.of(TestDataRule.APPLE.key, "name-1"),
        List.of(new Identifier("ipni", "77-1")));
    }

    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    var rows = readArchiveTsv(exp.getArchive(), ColdpTerm.NameUsage.simpleName() + ".tsv");
    var row = rows.stream().filter(r -> "root-1".equals(r.get(ColdpTerm.ID))).findFirst().orElse(null);
    assertNotNull("root-1 usage missing from export", row);
    assertEquals("tsn:12345,col:ABC", row.get(ColdpTerm.alternativeID));
    assertEquals("ipni:77-1", row.get(ColdpTerm.nameAlternativeID));
  }

  /**
   * Reads a tabular file from the zipped export archive into a list of column maps keyed by ColdpTerm.
   */
  private List<Map<ColdpTerm, String>> readArchiveTsv(java.io.File archive, String entryName) throws IOException {
    List<Map<ColdpTerm, String>> rows = new ArrayList<>();
    for (Map<String, String> raw : readArchiveRows(archive, entryName)) {
      Map<ColdpTerm, String> row = new HashMap<>();
      raw.forEach((col, value) -> {
        // header cells are prefixed names like "col:alternativeID" - strip the prefix
        String local = col.contains(":") ? col.substring(col.indexOf(':') + 1) : col;
        ColdpTerm t = ColdpTerm.find(local, false);
        if (t != null) {
          row.put(t, value);
        }
      });
      rows.add(row);
    }
    return rows;
  }

  /**
   * Media must export with the MIME type in col:type, the only type column ColDP defines for Media.
   * There is no col:format term for Media, so writing one aborted the whole export,
   * see https://github.com/CatalogueOfLife/checklistbank/issues/1711
   */
  @Test
  public void media() throws Exception {
    insertMedia(TestDataRule.APPLE.key, "root-1", MediaType.IMAGE, "image/jpeg");

    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    final String file = ColdpTerm.Media.simpleName() + ".tsv";
    var header = readArchiveHeader(exp.getArchive(), file);
    assertFalse("ColDP defines no format term for Media", header.contains(ColdpTerm.format.prefixedName()));

    var rows = readArchiveRows(exp.getArchive(), file);
    assertEquals(1, rows.size());
    var row = rows.get(0);
    assertEquals("image/jpeg", row.get(ColdpTerm.type.prefixedName()));
    assertEquals("https://example.org/media/root-1.jpg", row.get(ColdpTerm.url.prefixedName()));
  }

  /**
   * Without a MIME type col:type falls back to the primary type, which the ColDP spec also allows.
   */
  @Test
  public void mediaWithoutFormat() throws Exception {
    insertMedia(TestDataRule.APPLE.key, "root-1", MediaType.VIDEO, null);

    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    var rows = readArchiveRows(exp.getArchive(), ColdpTerm.Media.simpleName() + ".tsv");
    assertEquals(1, rows.size());
    assertEquals("video", rows.get(0).get(ColdpTerm.type.prefixedName()));
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
