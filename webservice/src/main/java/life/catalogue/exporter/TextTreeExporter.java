package life.catalogue.exporter;

import com.codahale.metrics.Timer;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.db.tree.TextTreePrinter;
import life.catalogue.img.ImageService;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

public class TextTreeExporter extends DatasetExporter {
  private static final Logger LOG = LoggerFactory.getLogger(TextTreeExporter.class);

  public TextTreeExporter(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService, Timer timer) {
    super(req, userKey, DataFormat.TEXT_TREE, false, factory, cfg, imageService, timer);
  }

  @Override
  public void export() throws Exception {
    File f = new File(tmpDir, "dataset-"+req.getDatasetKey()+".txt");
    try (Writer writer = UTF8IoUtils.writerFromFile(f)) {
      TextTreePrinter printer = PrinterFactory.dataset(TextTreePrinter.class, req.getDatasetKey(), req.getTaxonID(), req.isSynonyms(), req.getMinRank(), factory, writer);
      int cnt = printer.print();
      LOG.info("Written {} taxa to text tree for dataset {}", cnt, req.getDatasetKey());
      counter.set(printer.getCounter());
    }
  }

}
