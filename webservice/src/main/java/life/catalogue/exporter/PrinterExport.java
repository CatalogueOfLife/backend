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

public abstract class PrinterExport<T extends AbstractPrinter> extends DatasetExportJob {
  private static final Logger LOG = LoggerFactory.getLogger(PrinterExport.class);

  private final Class<T> printerClass;
  private final String printerName;

  PrinterExport(Class<T> printerClass, String printerName, DataFormat requiredFormat,
                ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService
  ) {
    super(req, userKey, requiredFormat, false, factory, cfg, imageService);
    this.printerClass = printerClass;
    this.printerName = printerName;
  }

  @Override
  protected void export() throws Exception {
    File f = new File(tmpDir, filename());
    try (Writer writer = UTF8IoUtils.writerFromFile(f)) {
      T printer = PrinterFactory.dataset(printerClass, req.toTreeTraversalParameter(), null, req.getExtinct(), null, null, factory, writer);
      modifyPrinter(printer);
      int cnt = printer.print();
      LOG.info("Written {} taxa to {} for dataset {}", cnt, printerName, req.getDatasetKey());
      counter.set(printer.getCounter());
    }
  }

  abstract protected String filename();

  void modifyPrinter(T printer) {
    // nothing by default
  }

}
