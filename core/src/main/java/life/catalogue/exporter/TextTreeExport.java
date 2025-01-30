package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.TextTreePrinter;

import org.apache.ibatis.session.SqlSessionFactory;

public class TextTreeExport extends PrinterExport<TextTreePrinter> {
  public TextTreeExport(ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    super(TextTreePrinter.class, "text tree", DataFormat.TEXT_TREE, req, userKey, factory, cfg, imageService);
  }

  @Override
  protected String filename() {
    return "dataset-"+req.getDatasetKey()+".txtree";
  }

  @Override
  void modifyPrinter(TextTreePrinter printer) {
    if (req.isExtended()) {
      printer.showIDs();
      printer.showExtendedInfos();
    }
  }
}
