package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.db.tree.TextTreePrinter;

import java.io.File;
import java.io.Writer;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * DAO giving read and write access to potentially large text trees and name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public class FileMetricsSectorDao extends FileMetricsDao<DSID<Integer>> {

  public FileMetricsSectorDao(SqlSessionFactory factory, File repo) {
    super("dataset", factory, repo);
  }

  @Override
  TextTreePrinter ttPrinter(DSID<Integer> key, SqlSessionFactory factory, Writer writer) {
    return TextTreePrinter.sector(key, factory, writer);
  }

  @Override
  public File subdir(DSID<Integer> key) {
    File dDir = FileMetricsDatasetDao.datasetDir(repo, key.getDatasetKey());
    return new File(dDir, "sector/" + key.getId());
  }

  @Override
  DSID<Integer> sectorKey(DSID<Integer> key) {
    return key;
  }

}
