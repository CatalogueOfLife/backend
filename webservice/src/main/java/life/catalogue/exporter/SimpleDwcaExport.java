package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.TabularFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.ColdpPrinter;

import life.catalogue.printer.DwcaPrinter;

import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.DwcTerm;

import java.util.List;

public class SimpleDwcaExport extends PrinterExport {

  private SimpleDwcaExport(Class<? extends DwcaPrinter> clazz, ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(clazz, "DwCA", "tsv", DataFormat.DWCA, req, userKey, factory, cfg, imageService);
  }

  public static SimpleDwcaExport build(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    return new SimpleDwcaExport(req.getTabFormat() == TabularFormat.CSV ? DwcaPrinter.CSV.class : DwcaPrinter.TSV.class, req, userKey, factory, cfg, imageService);
  }
}
