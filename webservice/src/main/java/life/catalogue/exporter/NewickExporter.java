package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.tree.NewickPrinter;
import life.catalogue.db.tree.PrinterFactory;
import life.catalogue.img.ImageService;

import java.io.File;
import java.io.Writer;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewickExporter extends DatasetExporter {
  private static final Logger LOG = LoggerFactory.getLogger(NewickExporter.class);

  public NewickExporter(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(req, userKey, DataFormat.NEWICK, false, factory, cfg, imageService);
    if (req.isSynonyms()) {
      throw new IllegalArgumentException("The Newick format does not support synonyms");
    }  }

  @Override
  public void export() throws Exception {
    // do we have a full dataset export request?
    File f = new File(tmpDir, "dataset-"+req.getDatasetKey()+".nhx");
    try (Writer writer = UTF8IoUtils.writerFromFile(f)) {
      NewickPrinter printer = PrinterFactory.dataset(NewickPrinter.class, req.getDatasetKey(), req.getTaxonID(), req.isSynonyms(), req.getMinRank(), factory, writer);
      printer.useExtendedFormat();
      int cnt = printer.print();
      LOG.info("Written {} usages to Newick tree for dataset {}", cnt, req.getDatasetKey());
      counter.set(printer.getCounter());
    }
  }

}
