package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.printer.AbstractPrinter;
import life.catalogue.printer.DotPrinter;
import life.catalogue.printer.NewickPrinter;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.Writer;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewickExport extends PrinterExport<NewickPrinter> {

  public NewickExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(NewickPrinter.class, "Newick", "nhx", DataFormat.NEWICK, req, userKey, factory, cfg, imageService);
    if (req.isSynonyms()) {
      throw new IllegalArgumentException("The Newick format does not support synonyms");
    }
  }

  @Override
  void modifyPrinter(NewickPrinter printer) {
    if (req.isExtended()) {
      printer.useExtendedFormat();
    }
  }
}
