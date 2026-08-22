package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.config.DiffConfig;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.SectorImportMapper;

import java.util.List;
import java.util.function.Supplier;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.collect.Lists;

public class SectorDiffService extends BaseDiffService<DSID<Integer>> {

  public SectorDiffService(SqlSessionFactory factory, FileMetricsSectorDao dao, DiffConfig diffCfg) {
    super(dao, factory, diffCfg);
  }

  @Override
  int[] parseAttempts(DSID<Integer> sectorKey, String attempts) {
    return parseAttempts(attempts, new Supplier<List<? extends ImportAttempt>>() {
      @Override
      public List<? extends ImportAttempt> get() {
        try (SqlSession session = factory.openSession(true)) {
          return session.getMapper(SectorImportMapper.class)
              .list(sectorKey.getId(), sectorKey.getDatasetKey(), null, Lists.newArrayList(JobStatus.FINISHED), null, null, new Page(0, 2));
        }
      }
    });
  }

}
