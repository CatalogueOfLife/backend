package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.FileMetricsSectorDao;
import life.catalogue.db.mapper.SectorImportMapper;

import java.util.List;
import java.util.function.Supplier;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.collect.Lists;

public class SectorDiffService extends BaseDiffService<DSID<Integer>> {

  public SectorDiffService(SqlSessionFactory factory, FileMetricsSectorDao dao, int timeoutInSeconds) {
    super(dao, factory, timeoutInSeconds);
  }

  @Override
  int[] parseAttempts(DSID<Integer> sectorKey, String attempts) {
    return parseAttempts(attempts, new Supplier<List<? extends ImportAttempt>>() {
      @Override
      public List<? extends ImportAttempt> get() {
        try (SqlSession session = factory.openSession(true)) {
          return session.getMapper(SectorImportMapper.class)
              .list(sectorKey.getId(), sectorKey.getDatasetKey(), null, Lists.newArrayList(ImportState.FINISHED), null, new Page(0, 2));
        }
      }
    });
  }

}
