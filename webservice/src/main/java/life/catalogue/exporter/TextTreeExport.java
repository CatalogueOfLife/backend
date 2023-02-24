package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.Writer;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;

public class TextTreeExport extends DatasetExportJob {
  private static final Logger LOG = LoggerFactory.getLogger(TextTreeExport.class);

  public TextTreeExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(req, userKey, DataFormat.TEXT_TREE, false, factory, cfg, imageService);
  }

  @Override
  public void export() throws Exception {
    File f = new File(tmpDir, "dataset-"+req.getDatasetKey()+".txt");
    try (Writer writer = UTF8IoUtils.writerFromFile(f)) {
      TextTreePrinter printer = PrinterFactory.dataset(TextTreePrinter.class, req.toTreeTraversalParameter(), factory, writer);
      int cnt = printer.print();
      LOG.info("Written {} taxa to text tree for dataset {}", cnt, req.getDatasetKey());
      counter.set(printer.getCounter());
    }
  }

}
