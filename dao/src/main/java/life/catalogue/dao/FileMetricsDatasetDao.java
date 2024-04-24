package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAO giving read and write access to potentially large text trees and name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public class FileMetricsDatasetDao extends FileMetricsDao<Integer> {
  private static final Logger LOG = LoggerFactory.getLogger(FileMetricsDatasetDao.class);

  public FileMetricsDatasetDao(SqlSessionFactory factory, File repo) {
    super("dataset", factory, repo);
  }

  @Override
  public File subdir(Integer key) {
    return datasetDir(repo, key);
  }

  static File datasetDir(File repo, int key) {
    return new File(repo, "dataset/" + bucket(key) + "/" + key);
  }

  @Override
  DSID<Integer> sectorKey(Integer key) {
    return DSID.of(key, null);
  }

  public int updateTree(Integer datasetKey, Integer storeKey, int attempt) throws IOException {
    try (Writer writer = UTF8IoUtils.writerFromGzipFile(treeFile(storeKey, attempt))) {
      TextTreePrinter ttp = PrinterFactory.dataset(TextTreePrinter.class, datasetKey, factory, writer);
      int count = ttp.print();
      LOG.info("Written text tree with {} lines for {} {}-{}", count, type, datasetKey, attempt);
      return count;
    }
  }

  /**
   * Assigns a given evenly distributed integer to a bucket of max 1000 items so we do not overload the filesystem
   * @param x
   * @return 000-999
   */
  @VisibleForTesting
  static String bucket(int x) {
    return String.format("%03d", Math.abs(x) % 1000);
  }
}
