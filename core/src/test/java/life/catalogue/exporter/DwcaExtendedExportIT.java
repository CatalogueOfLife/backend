package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.util.RankUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.EmptySqlSessionFactory;
import life.catalogue.img.ImageService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

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