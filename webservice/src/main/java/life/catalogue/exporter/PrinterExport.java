package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.img.ImageService;
import life.catalogue.printer.AbstractPrinter;
import life.catalogue.printer.PrinterFactory;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Writer;

public abstract class PrinterExport extends DatasetExportJob {
  private static final Logger LOG = LoggerFactory.getLogger(PrinterExport.class);

  private final Class<? extends AbstractPrinter> printerClass;
  private final String printerName;
  private final String fileSuffix;

  PrinterExport(Class<? extends AbstractPrinter> printerClass, String printerName, String fileSuffix, DataFormat requiredFormat,
                ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService
  ) {
    super(req, userKey, requiredFormat, false, factory, cfg, imageService);
    this.fileSuffix = fileSuffix;
    this.printerClass = printerClass;
    this.printerName = printerName;
  }

  @Override
  protected void export() throws Exception {
    File f = new File(tmpDir, "dataset-"+req.getDatasetKey()+"."+fileSuffix);
    try (Writer writer = UTF8IoUtils.writerFromFile(f)) {
      AbstractPrinter printer = PrinterFactory.dataset(printerClass, req.toTreeTraversalParameter(), factory, writer);
      modifyPrinter(printer);
      int cnt = printer.print();
      LOG.info("Written {} taxa to {} for dataset {}", cnt, printerName, req.getDatasetKey());
      counter.set(printer.getCounter());
    }
  }

  void modifyPrinter(AbstractPrinter printer) {
    // nothing by default
  }

}
