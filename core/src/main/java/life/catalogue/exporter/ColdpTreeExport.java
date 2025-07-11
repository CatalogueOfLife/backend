package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.ColdpTreePrinter;

import java.io.IOException;

import org.apache.ibatis.session.SqlSessionFactory;

public class ColdpTreeExport extends PrinterExport<ColdpTreePrinter> {
  public ColdpTreeExport(ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    super(ColdpTreePrinter.class, "coldp tree", DataFormat.COLDP, req, userKey, factory, cfg, imageService);
  }

  @Override
  protected String filename() {
    return "dataset-"+req.getDatasetKey()+".tsv";
  }

  @Override
  void modifyPrinter(ColdpTreePrinter printer) throws IOException {
    if (req.isTaxGroups()) {
      printer.showTaxGroups();
    }
  }
}
