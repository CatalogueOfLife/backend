package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.DotPrinter;

import org.apache.ibatis.session.SqlSessionFactory;

public class DotExport extends PrinterExport<DotPrinter> {

  public DotExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(DotPrinter.class, "DOT", "gv", DataFormat.DOT, req, userKey, factory, cfg, imageService);
  }

}
