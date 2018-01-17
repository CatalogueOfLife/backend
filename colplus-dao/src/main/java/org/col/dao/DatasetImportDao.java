package org.col.dao;

import com.google.common.base.Preconditions;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.api.Page;
import org.col.api.ResultPage;
import org.col.api.vocab.ImportState;
import org.col.db.mapper.DatasetImportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class DatasetImportDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetImportDao.class);

  private final SqlSessionFactory factory;


  public DatasetImportDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public ResultPage<DatasetImport> list(Page page) {
    try (SqlSession session = factory.openSession(true)){
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      return new ResultPage<>(page, mapper.count(), mapper.list(page));
    }
  }

  /**
   * Create a new running dataset import with the next attempt
   */
  public DatasetImport createRunning(Dataset d) {
    // build new import
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(d.getKey());
    di.setState(ImportState.RUNNING);
    di.setDownloadUri(d.getDataAccess());

    try (SqlSession session = factory.openSession(true)){
      session.getMapper(DatasetImportMapper.class).create(di);
    }

    return di;
  }

  /**
   * Updates a running dataset import instance with metrics and success state.
   */
  public void updateImportSuccess(DatasetImport di) {
    try (SqlSession session = factory.openSession(true)){
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      // generate new count metrics
      DatasetImport m = mapper.metrics(di.getDatasetKey());
      // update metrics instance with existing infos
      m.setDatasetKey(di.getDatasetKey());
      m.setAttempt(di.getAttempt());
      m.setStarted(di.getStarted());
      m.setDownload(di.getDownload());
      m.setFinished(LocalDateTime.now());
      m.setState(ImportState.FINISHED);
      m.setError(null);
      update(m, mapper);
    }
  }

  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public void updateImportFailure(DatasetImport di, Exception e) {
    di.setFinished(LocalDateTime.now());
    di.setState(ImportState.FAILED);
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

  public void update(DatasetImport di) {
    try (SqlSession session = factory.openSession(true)){
      update(di, session.getMapper(DatasetImportMapper.class));
    }
  }

  private void update(DatasetImport di, DatasetImportMapper mapper) {
    Preconditions.checkNotNull(di.getDatasetKey(), "datasetKey required for update");
    Preconditions.checkNotNull(di.getAttempt(), "attempt required for update");
    mapper.update(di);
  }
}
