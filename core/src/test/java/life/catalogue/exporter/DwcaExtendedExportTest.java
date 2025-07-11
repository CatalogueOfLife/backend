package life.catalogue.exporter;

import life.catalogue.TestConfigs;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.util.RankUtils;
import life.catalogue.api.vocab.EntityType;

import java.util.Arrays;
import java.util.stream.Collectors;

import life.catalogue.img.ImageService;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DwcaExtendedExportTest {

    @Test
    public void dwcRankTerms() {
      var req = new ExportRequest();
      req.setDatasetKey(3);
      var exp = new TestDwcaExtendedExport(req, 1, null, new TestConfigs(), null);
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
      protected Dataset loadDataset(SqlSessionFactory factory, int datasetKey) {
        return new Dataset();
      }

      @Override
      protected void createExport(DatasetExport export) {
        // nothing
      }
    }

}