package life.catalogue.dao;

import life.catalogue.api.model.DSID;

import java.io.File;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * DAO giving read and write access to potentially large name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public class FileMetricsSectorDao extends FileMetricsDao<DSID<Integer>> {

  public FileMetricsSectorDao(SqlSessionFactory factory, File repo) {
    super("dataset", factory, repo);
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
