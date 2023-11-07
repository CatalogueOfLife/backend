package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.ColdpPrinter;

import org.apache.ibatis.session.SqlSessionFactory;

public class SimpleColdpExport extends PrinterExport {

  public SimpleColdpExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(ColdpPrinter.class, "ColDP", "tsv", DataFormat.COLDP, req, userKey, factory, cfg, imageService);
  }
}
