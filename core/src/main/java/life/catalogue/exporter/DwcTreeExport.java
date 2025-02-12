package life.catalogue.exporter;

import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import life.catalogue.printer.DwcTreePrinter;

import org.apache.ibatis.session.SqlSessionFactory;

import java.io.IOException;

public class DwcTreeExport extends PrinterExport<DwcTreePrinter> {
  public DwcTreeExport(ExportRequest req, int userKey, SqlSessionFactory factory, ExporterConfig cfg, ImageService imageService) {
    super(DwcTreePrinter.class, "dwc tree", DataFormat.DWCA, req, userKey, factory, cfg, imageService);
  }

  @Override
  protected String filename() {
    return "dataset-"+req.getDatasetKey()+".tsv";
  }

  @Override
  void modifyPrinter(DwcTreePrinter printer) throws IOException {
    printer.initWriter(req.isAddTaxGroups());
  }
}
