package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.model.TaxonProperty;
import life.catalogue.api.util.RankUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.MediaType;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.Users;
import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.TaxonMapper;
import life.catalogue.db.mapper.TaxonPropertyMapper;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DwcaExtendedExportIT extends ExportTest {

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

  /**
   * Unlike ColDP, DwC-A writes the related *name* id into originalNameUsageID. The apple data also holds a
   * SPELLING_CORRECTION relation on name-2, which must not show up as an original name.
   * The value is resolved by the export query (NameUsageMapper BASIONYM_JOIN), not by a lookup per usage.
   */
  @Test
  public void originalNameUsageID() throws Exception {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var rel = new NameRelation();
      rel.setDatasetKey(TestDataRule.APPLE.key);
      rel.setType(NomRelType.BASIONYM);
      rel.setNameId("name-1");
      rel.setRelatedNameId("name-3");
      rel.applyUser(Users.TESTER);
      session.getMapper(NameRelationMapper.class).create(rel);
    }

    DwcaExtendedExport exp = new DwcaExtendedExport(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    var rows = readArchiveRows(exp.getArchive(), DwcTerm.Taxon.simpleName() + ".tsv");
    var root1 = rows.stream().filter(r -> "root-1".equals(r.get(DwcTerm.taxonID.prefixedName()))).findFirst().orElse(null);
    assertEquals("name-3", root1.get(DwcTerm.originalNameUsageID.prefixedName()));

    var root2 = rows.stream().filter(r -> "root-2".equals(r.get(DwcTerm.taxonID.prefixedName()))).findFirst().orElse(null);
    assertTrue("a SPELLING_CORRECTION relation is not a basionym", StringUtils.isBlank(root2.get(DwcTerm.originalNameUsageID.prefixedName())));
  }

  /**
   * DwC-A writes reference citations inline where ColDP writes ids, so namePublishedIn and nameAccordingTo
   * must carry the citation text. Both are joined in by the core export query rather than looked up per
   * usage, see NameUsageMapper inclCitations.
   */
  @Test
  public void inlineCitations() throws Exception {
    // apple gives name-1 a publishedIn of ref-1 but leaves every accordingTo empty
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      var t = tm.get(DSID.of(TestDataRule.APPLE.key, "root-1"));
      t.setAccordingToId("ref-1b");
      tm.update(t);
    }

    DwcaExtendedExport exp = new DwcaExtendedExport(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    var rows = readArchiveRows(exp.getArchive(), DwcTerm.Taxon.simpleName() + ".tsv");
    var root1 = rows.stream().filter(r -> "root-1".equals(r.get(DwcTerm.taxonID.prefixedName()))).findFirst().orElse(null);
    assertEquals("ref-1", root1.get(DwcTerm.namePublishedIn.prefixedName()));
    assertEquals("ref-1b", root1.get(DwcTerm.nameAccordingTo.prefixedName()));

    // root-2's name-2 has no publishedIn reference at all
    var root2 = rows.stream().filter(r -> "root-2".equals(r.get(DwcTerm.taxonID.prefixedName()))).findFirst().orElse(null);
    assertTrue(StringUtils.isBlank(root2.get(DwcTerm.namePublishedIn.prefixedName())));
  }

  /**
   * Media were silently dropped from DwC-A exports because no Multimedia extension was ever defined,
   * see https://github.com/CatalogueOfLife/checklistbank/issues/1711
   */
  @Test
  public void media() throws Exception {
    insertMedia(TestDataRule.APPLE.key, "root-1", MediaType.IMAGE, "image/jpeg");

    DwcaExtendedExport exp = new DwcaExtendedExport(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    var rows = readArchiveRows(exp.getArchive(), GbifTerm.Multimedia.simpleName() + ".tsv");
    assertEquals(1, rows.size());
    var row = rows.get(0);
    assertEquals("root-1", row.get(DwcTerm.taxonID.prefixedName()));
    assertEquals("https://example.org/media/root-1.jpg", row.get(DcTerm.identifier.prefixedName()));
    assertEquals("https://example.org/page/root-1", row.get(DcTerm.references.prefixedName()));
    assertEquals("image/jpeg", row.get(DcTerm.format.prefixedName()));
    assertEquals("image", row.get(DcTerm.type.prefixedName()));
    assertEquals("Picture of root-1", row.get(DcTerm.title.prefixedName()));
    assertEquals(License.CC0.getUrl(), row.get(DcTerm.license.prefixedName()));

    // an extension file that is not declared in meta.xml is ignored by every DwC-A reader
    String meta = readArchiveEntry(exp.getArchive(), "meta.xml");
    assertTrue("Multimedia extension missing from meta.xml", meta.contains(GbifTerm.Multimedia.qualifiedName()));
  }

  /**
   * The MeasurementOrFact extension wrote col:taxonID into a file whose id column is dwc:taxonID,
   * which aborted the export for any dataset with taxon properties.
   */
  @Test
  public void taxonProperties() throws Exception {
    TaxonProperty tp = new TaxonProperty();
    tp.setDatasetKey(TestDataRule.APPLE.key);
    tp.setProperty("habitat");
    tp.setValue("terrestrial");
    tp.applyUser(Users.TESTER);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(TaxonPropertyMapper.class).create(tp, "root-1");
    }

    DwcaExtendedExport exp = new DwcaExtendedExport(new ExportRequest(TestDataRule.APPLE.key, DataFormat.DWCA), Users.TESTER, SqlSessionFactoryRule.getSqlSessionFactory(), cfg, ImageService.passThru());
    exp.run();
    assertExportExists(exp.getArchive());

    var rows = readArchiveRows(exp.getArchive(), DwcTerm.MeasurementOrFact.simpleName() + ".tsv");
    assertEquals(1, rows.size());
    assertEquals("root-1", rows.get(0).get(DwcTerm.taxonID.prefixedName()));
    assertEquals("habitat", rows.get(0).get(DwcTerm.measurementType.prefixedName()));

    String meta = readArchiveEntry(exp.getArchive(), "meta.xml");
    assertTrue("MeasurementOrFact extension missing from meta.xml", meta.contains(DwcTerm.MeasurementOrFact.qualifiedName()));
  }

  @Test
  public void dwcRankTerms() {
    var req = new ExportRequest();
    req.setDatasetKey(3);
    req.setFormat(DataFormat.DWCA);
    var exp = new TestDwcaExtendedExport(req, 1, SqlSessionFactoryRule.getSqlSessionFactory(), new TestConfigs(), null);
    var terms = exp.define(EntityType.NAME_USAGE);
    var set = Arrays.stream(terms).collect(Collectors.toSet());
    for (var term : RankUtils.RANK2DWC.values()) {
      assertTrue("missing "+term, set.contains(term));
    }
  }

  static class TestDwcaExtendedExport extends DwcaExtendedExport {

    public TestDwcaExtendedExport(ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
      super(req, userKey, factory, cfg, imageService);
    }

    @Override
    protected void createExport(DatasetExport export) {
      // nothing
    }
  }
}