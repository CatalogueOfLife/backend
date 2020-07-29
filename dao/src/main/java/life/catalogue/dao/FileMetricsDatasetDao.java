package life.catalogue.dao;

import com.google.common.annotations.VisibleForTesting;
import life.catalogue.api.model.DSID;
import life.catalogue.db.tree.TextTreePrinter;
import org.apache.ibatis.session.SqlSessionFactory;

import java.io.File;
import java.io.Writer;

/**
 * DAO giving read and write access to potentially large text trees and name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public class FileMetricsDatasetDao extends FileMetricsDao<Integer> {

  public FileMetricsDatasetDao(SqlSessionFactory factory, File repo) {
    super("dataset", factory, repo);
  }

  @Override
  TextTreePrinter ttPrinter(Integer key, SqlSessionFactory factory, Writer writer) {
    return TextTreePrinter.dataset(key, factory, writer);
  }

  @Override
  File subdir(Integer key) {
    return datasetDir(repo, key);
  }

  static File datasetDir(File repo, int key) {
    return new File(repo, "dataset/" + bucket(key) + "/" + key);
  }

  @Override
  DSID<Integer> sectorKey(Integer key) {
    return DSID.of(key, null);
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
