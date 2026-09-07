package life.catalogue.exporter;

import life.catalogue.api.model.CslData;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.Identifier;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Reference;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.MediaType;
import life.catalogue.api.vocab.Users;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

  /**
   * ColDP links usages, so basionymID must carry the usage id of the related name - here name-3, whose
   * usage is the synonym s1. The apple data also holds a SPELLING_CORRECTION relation on name-2, which
   * must not show up as a basionym.
   * The value is resolved by the export query (NameUsageMapper BASIONYM_JOIN), not by a lookup per usage.
   */
  @Test
  public void basionymID() throws Exception {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var rel = new NameRelation();
      rel.setDatasetKey(TestDataRule.APPLE.key);
      rel.setType(NomRelType.BASIONYM);
      rel.setNameId("name-1");
      rel.setRelatedNameId("name-3");
      rel.applyUser(Users.TESTER);
      session.getMapper(NameRelationMapper.class).create(rel);
    }

    ColdpExtendedExport exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    var rows = readArchiveTsv(exp.getArchive(), ColdpTerm.NameUsage.simpleName() + ".tsv");
    var root1 = rows.stream().filter(r -> "root-1".equals(r.get(ColdpTerm.ID))).findFirst().orElse(null);
    assertNotNull("root-1 usage missing from export", root1);
    assertEquals("s1", root1.get(ColdpTerm.basionymID));

    var root2 = rows.stream().filter(r -> "root-2".equals(r.get(ColdpTerm.ID))).findFirst().orElse(null);
    assertNotNull("root-2 usage missing from export", root2);
    assertTrue("a SPELLING_CORRECTION relation is not a basionym", StringUtils.isBlank(root2.get(ColdpTerm.basionymID)));

    // every other usage is unrelated and must stay empty
    var s1 = rows.stream().filter(r -> "s1".equals(r.get(ColdpTerm.ID))).findFirst().orElse(null);
    assertNotNull("s1 usage missing from export", s1);
    assertTrue(StringUtils.isBlank(s1.get(ColdpTerm.basionymID)));
  }

  /**
   * A filtered export fetches the ids it wants in batches of ArchiveExport.ID_BATCH_SIZE. A batch of one
   * is exactly the per id fetching this replaced, so driving the same export at both sizes and comparing
   * the archives pins the batching down against the behaviour it inherited.
   */
  @Test
  public void filteredBatchesMatchSingleIdFetches() throws Exception {
    req.setSynonyms(false); // any filter makes fullDataset false, so both branches become reachable

    final int origBatchSize = ArchiveExport.ID_BATCH_SIZE;
    Map<String, List<Map<String, String>>> perId = new LinkedHashMap<>();
    Map<String, List<Map<String, String>>> batched = new LinkedHashMap<>();
    final List<String> files = List.of(
      ColdpTerm.NameUsage.simpleName() + ".tsv",
      ColdpTerm.VernacularName.simpleName() + ".tsv",
      ColdpTerm.Distribution.simpleName() + ".tsv",
      ColdpTerm.NameRelation.simpleName() + ".tsv",
      ColdpTerm.Reference.simpleName() + ".tsv"
    );
    try {
      ArchiveExport.ID_BATCH_SIZE = 1; // one id per query, i.e. the old per id fetching
      var exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
      exp.run();
      assertExportExists(exp.getArchive());
      for (String f : files) {
        perId.put(f, readArchiveRows(exp.getArchive(), f));
      }

      ArchiveExport.ID_BATCH_SIZE = 1000; // every id of this little dataset in one batch
      exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
      exp.run();
      assertExportExists(exp.getArchive());
      for (String f : files) {
        batched.put(f, readArchiveRows(exp.getArchive(), f));
      }
    } finally {
      ArchiveExport.ID_BATCH_SIZE = origBatchSize;
    }

    for (String f : files) {
      // neither branch promises an order, so compare as sets
      assertEquals(f, new HashSet<>(perId.get(f)), new HashSet<>(batched.get(f)));
    }
    // and the filter really did keep rows, rather than both branches agreeing on nothing
    assertFalse("expected the root-2 subtree to export some usages", perId.get(files.get(0)).isEmpty());
  }

  /**
   * An entity the dataset holds no record of skips its queries - a filtered export asks for its ids in
   * batches, so an empty entity still cost one query per batch. The file must still be written, empty but
   * for its header: DwC-A's meta.xml declares its extensions unconditionally and a reader chokes on a
   * declared file that is not there.
   */
  @Test
  public void skipEntitiesWithoutRecords() throws Exception {
    // apple really has 3 vernacular names and 5 distributions; claim there is not one vernacular
    final DatasetImport di = new DatasetImport();
    di.setVernacularCount(0);

    var exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru()) {
      @Override
      DatasetImport loadMetrics() {
        return di;
      }
    };
    exp.run();
    assertExportExists(exp.getArchive());

    // the pass was skipped, so none of the three real records made it out
    final String vernacular = ColdpTerm.VernacularName.simpleName() + ".tsv";
    assertTrue(readArchiveRows(exp.getArchive(), vernacular).isEmpty());
    // but the file is there with its header
    assertTrue("the skipped entity must still write its header",
      readArchiveHeader(exp.getArchive(), vernacular).contains(ColdpTerm.name.prefixedName()));

    // an entity with no count at all is not skipped - the gate only ever acts on evidence
    assertEquals(5, readArchiveRows(exp.getArchive(), ColdpTerm.Distribution.simpleName() + ".tsv").size());
  }

  /**
   * Apple is a PROJECT, which can be edited between imports without a new attempt, so its counts need not
   * describe the tables. Nothing may be skipped on them.
   */
  @Test
  public void noMetricsGateForProjects() {
    var exp = new ColdpExtendedExport(req, Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    assertNull(exp.loadMetrics());
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
