package life.catalogue.db.tree;

import com.google.common.collect.Lists;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.NamesTreeDao;
import life.catalogue.db.mapper.SectorImportMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.function.Supplier;

public class SectorDiffService extends BaseDiffService {

  public SectorDiffService(SqlSessionFactory factory, NamesTreeDao dao) {
    super(NamesTreeDao.Context.SECTOR, dao, factory);
  }

  @Override
  int[] parseAttempts(int sectorKey, String attempts) {
    return parseAttempts(attempts, new Supplier<List<? extends ImportAttempt>>() {
      @Override
      public List<? extends ImportAttempt> get() {
        try (SqlSession session = factory.openSession(true)) {
          return session.getMapper(SectorImportMapper.class)
              .list(sectorKey, null, null, Lists.newArrayList(ImportState.FINISHED), new Page(0, 2));
        }
      }
    });
  }

}
