package life.catalogue.db.tree;

import com.google.common.collect.Lists;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;
import java.util.function.Supplier;

public class DatasetDiffService extends BaseDiffService<Integer> {

  public DatasetDiffService(SqlSessionFactory factory, FileMetricsDatasetDao dao) {
    super(dao, factory);
  }

  @Override
  int[] parseAttempts(Integer datasetKey, String attempts) {
    return parseAttempts(attempts, new Supplier<List<? extends ImportAttempt>>() {
      @Override
      public List<? extends ImportAttempt> get() {
        try (SqlSession session = factory.openSession(true)) {
          return session.getMapper(DatasetImportMapper.class)
              .list(datasetKey, Lists.newArrayList(ImportState.FINISHED), new Page(0, 2));
        }
      }
    });
  }

}
