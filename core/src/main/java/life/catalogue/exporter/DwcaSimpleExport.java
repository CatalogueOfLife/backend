package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.common.io.Resources;
import life.catalogue.img.ImageService;
import life.catalogue.printer.DwcaPrinter;

import java.io.File;

import org.apache.ibatis.session.SqlSessionFactory;

public class DwcaSimpleExport<T extends DwcaPrinter> extends PrinterExport<T> {

  private DwcaSimpleExport(Class<T> clazz, ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    super(clazz, "DwCA", DataFormat.DWCA, req, userKey, factory, cfg, imageService);
  }

  public static DwcaSimpleExport<?> build(ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    return new DwcaSimpleExport(req.getTabFormat() == TabularFormat.CSV ? DwcaPrinter.CSV.class : DwcaPrinter.TSV.class, req, userKey, factory, cfg, imageService);
  }

  @Override
  protected String filename() {
    return "Taxon." + req.getTabFormat().name().toLowerCase();
  }

  @Override
  protected void export() throws Exception {
    super.export();
    // include a meta.xml file
    File meta = new File(tmpDir, "meta.xml");
    TabularFormat format = ObjectUtils.coalesce(req.getTabFormat(), TabularFormat.TSV);
    Resources.copy("export/dwca/simple-meta-"+ format.name().toLowerCase() +".xml", meta);
  }
}
