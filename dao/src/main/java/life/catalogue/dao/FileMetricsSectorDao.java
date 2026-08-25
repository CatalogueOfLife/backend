package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.SectorImportMapper;

import java.io.File;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * DAO giving read and write access to potentially large name lists
 * stored on the filesystem. We use compression to keep storage small.
 */
public class FileMetricsSectorDao extends FileMetricsDao<DSID<Integer>> {

  public FileMetricsSectorDao(SqlSessionFactory factory, File repo) {
    super("sector", factory, repo);
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

  @Override
  protected Integer persistedNameCount(DSID<Integer> key, int attempt) {
    try (SqlSession session = factory.openSession(true)) {
      var si = session.getMapper(SectorImportMapper.class).get(key, attempt);
      return si == null ? null : si.getNameCount();
    }
  }

}
