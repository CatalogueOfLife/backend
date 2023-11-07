package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.ColdpPrinter;

import life.catalogue.printer.DwcaPrinter;

import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.DwcTerm;

import java.util.List;

public class SimpleDwcaExport extends PrinterExport {

  public SimpleDwcaExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(DwcaPrinter.class, "DwCA", "tsv", DataFormat.DWCA, req, userKey, factory, cfg, imageService);
  }
}
