package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.NewickPrinter;

import org.apache.ibatis.session.SqlSessionFactory;

public class NewickExport extends PrinterExport<NewickPrinter> {

  public NewickExport(ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    super(NewickPrinter.class, "Newick", DataFormat.NEWICK, req, userKey, factory, cfg, imageService);
    if (req.isSynonyms()) {
      throw new IllegalArgumentException("The Newick format does not support synonyms");
    }
  }

  @Override
  protected String filename() {
    return "dataset-"+req.getDatasetKey()+".nhx";
  }

  @Override
  void modifyPrinter(NewickPrinter printer) {
    if (req.isExtended()) {
      printer.useExtendedFormat();
    }
  }
}
