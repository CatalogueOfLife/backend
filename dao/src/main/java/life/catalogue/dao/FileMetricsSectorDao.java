package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.db.mapper.SectorImportMapper;

import java.io.File;
import java.util.stream.Stream;

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

  /**
   * A sync that produced no names writes no file, so a missing file is not an error as long as the
   * sector import records a zero name count. Only a genuinely unknown attempt is a NotFound.
   */
  @Override
  public Stream<String> getNames(DSID<Integer> key, int attempt) {
    File f = namesFile(key, attempt);
    if (!f.exists()) {
      try (SqlSession session = factory.openSession(true)) {
        var si = session.getMapper(SectorImportMapper.class).get(key, attempt);
        if (si != null && (si.getNameCount() == null || si.getNameCount() == 0)) {
          return Stream.empty();
        }
      }
    }
    return super.getNames(key, attempt);
  }

}
