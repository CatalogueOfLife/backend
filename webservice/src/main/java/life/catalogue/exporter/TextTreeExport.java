package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.printer.NewickPrinter;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.Writer;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextTreeExport extends PrinterExport<TextTreePrinter> {
  public TextTreeExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(TextTreePrinter.class, "text tree", "txtree", DataFormat.TEXT_TREE, req, userKey, factory, cfg, imageService);
  }

  @Override
  void modifyPrinter(TextTreePrinter printer) {
    if (req.isExtended()) {
      printer.showIDs();
    }
  }
}
