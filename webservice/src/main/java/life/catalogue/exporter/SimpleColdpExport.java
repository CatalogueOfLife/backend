package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.ColdpPrinter;

import org.apache.ibatis.session.SqlSessionFactory;

public class SimpleColdpExport extends PrinterExport {

  private SimpleColdpExport(Class<? extends ColdpPrinter> clazz, ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(clazz, "ColDP", "tsv", DataFormat.COLDP, req, userKey, factory, cfg, imageService);
  }

  public static SimpleColdpExport build(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    return new SimpleColdpExport(req.getTabFormat() == TabularFormat.CSV ? ColdpPrinter.CSV.class : ColdpPrinter.TSV.class, req, userKey, factory, cfg, imageService);
  }

}
