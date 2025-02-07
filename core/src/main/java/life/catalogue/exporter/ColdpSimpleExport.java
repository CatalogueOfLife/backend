package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.ColdpPrinter;

import org.apache.ibatis.session.SqlSessionFactory;

public class ColdpSimpleExport<T extends ColdpPrinter> extends PrinterExport<T> {

  private ColdpSimpleExport(Class<T> clazz, ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    super(clazz, "ColDP", DataFormat.COLDP, req, userKey, factory, cfg, imageService);
  }

  public static ColdpSimpleExport<? extends ColdpPrinter> build(ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    if (req.getTabFormat() == TabularFormat.CSV) {
      return new ColdpSimpleExport<>(ColdpPrinter.CSV.class, req, userKey, factory, cfg, imageService);

    }
    return new ColdpSimpleExport<>(ColdpPrinter.TSV.class, req, userKey, factory, cfg, imageService);
  }

  @Override
  protected String filename() {
    return "NameUsage." + req.getTabFormat().name().toLowerCase();
  }

}
