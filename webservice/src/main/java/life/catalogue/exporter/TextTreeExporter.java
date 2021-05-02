package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.UTF8IoUtils;
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
  private File f;

  public TextTreeExporter(ExportRequest req, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(req, DataFormat.TEXT_TREE, factory, cfg, imageService);
    if (req.isExcel()) {
      throw new IllegalArgumentException("TextTree cannot be exported in Excel");
    }
    if (req.getExclusions() != null && !req.getExclusions().isEmpty()) {
      throw new IllegalArgumentException("TextTree cannot exclude taxa");
    }
  }

  @Override
  public void export() throws Exception {
    // do we have a full dataset export request?
    f = new File(tmpDir, "dataset-"+req.getDatasetKey()+".txt");
    try (Writer writer = UTF8IoUtils.writerFromFile(f)) {
      TextTreePrinter printer = TextTreePrinter.dataset(req.getDatasetKey(), req.getTaxonID(), req.getMinRank(), factory, writer);
      int cnt = printer.print();
      LOG.info("Written {} taxa to text tree for dataset {}", cnt, req.getDatasetKey());
    }
  }

  @Override
  protected void bundle() throws IOException {
    LOG.info("Compressing text tree to {}", archive.getAbsolutePath());
    FileUtils.forceMkdir(archive.getParentFile());
    CompressionUtil.zipFile(f, archive);
  }

}
