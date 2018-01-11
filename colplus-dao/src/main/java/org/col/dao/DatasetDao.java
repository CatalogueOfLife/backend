package org.col.dao;

import java.time.LocalDateTime;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.api.vocab.ImportState;
import org.col.db.KeyNotFoundException;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Preconditions;

public class DatasetDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDao.class);

  private final SqlSession session;
  private final DatasetMapper mapper;
  private final DatasetImportMapper diMapper;

  public DatasetDao(SqlSession sqlSession) {
    this.session = sqlSession;
    mapper = session.getMapper(DatasetMapper.class);
    diMapper = session.getMapper(DatasetImportMapper.class);
  }

  public Dataset get(int key) {
    Dataset result = mapper.get(key);
    if (result == null) {
      throw new KeyNotFoundException(Dataset.class, key);
    }
    return result;
  }

  public PagingResultSet<Dataset> search(String q, @Nullable Page page) {
    page = page == null ? new Page() : page;
    // String query = q + ":*"; // Enable "starts_with" term matching
    int total = mapper.count(q);
    List<Dataset> result = mapper.search(q, page);
    return new PagingResultSet<>(page, total, result);
  }

  /**
   * Creates a new successful dataset import instance with metrics
   */
  public DatasetImport startImport(Dataset dataset) {
    // build new import
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(dataset.getKey());
    di.setStarted(LocalDateTime.now());
    di.setState(ImportState.RUNNING);
    diMapper.create(di);
    return di;
  }

  /**
   * Updates a running dataset import instance with metrics and success state.
   */
  public void updateImportSuccess(DatasetImport di) {
    // generate new count metrics
    DatasetImport m = diMapper.metrics(di.getDatasetKey());
    // update metrics instance with existing infos
    m.setDatasetKey(di.getDatasetKey());
    m.setAttempt(di.getAttempt());
    m.setStarted(di.getStarted());
    m.setDownload(di.getDownload());
    m.setFinished(LocalDateTime.now());
    m.setState(ImportState.SUCCESS);
    m.setError(null);
    update(m);
  }

  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public void updateImportFailure(DatasetImport di, ImportState state, Exception e) {
    di.setFinished(LocalDateTime.now());
    di.setState(state);
    // System.out.println(ExceptionUtils.getMessage(e));
    di.setError(e.getMessage());
    update(di);
  }

  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public void updateImportUnchanged(DatasetImport di) {
    di.setFinished(LocalDateTime.now());
    di.setState(ImportState.UNCHANGED);
    di.setError(null);
    update(di);
  }

  private void update(DatasetImport di) {
    Preconditions.checkNotNull(di.getDatasetKey(), "datasetKey required for update");
    Preconditions.checkNotNull(di.getAttempt(), "attempt required for update");
    diMapper.update(di);
  }
}
