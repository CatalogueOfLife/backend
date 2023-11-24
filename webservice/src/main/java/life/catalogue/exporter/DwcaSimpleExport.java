package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.img.ImageService;

import life.catalogue.printer.DwcaPrinter;

import org.apache.ibatis.session.SqlSessionFactory;

public class DwcaSimpleExport<T extends DwcaPrinter> extends PrinterExport<T> {

  private DwcaSimpleExport(Class<T> clazz, ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(clazz, "DwCA", "tsv", DataFormat.DWCA, req, userKey, factory, cfg, imageService);
  }

  public static DwcaSimpleExport<?> build(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    return new DwcaSimpleExport(req.getTabFormat() == TabularFormat.CSV ? DwcaPrinter.CSV.class : DwcaPrinter.TSV.class, req, userKey, factory, cfg, imageService);
  }
}
