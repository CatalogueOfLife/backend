package org.col.db.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.exception.NotFoundException;
import org.col.api.model.Dataset;
import org.col.api.model.DatasetImport;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.ImportState;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetImportDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetImportDao.class);

  private final SqlSessionFactory factory;


  public DatasetImportDao(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public List<DatasetImport> listByDataset(int key, @Nullable ImportState state, int limit) {
    try (SqlSession session = factory.openSession(true)){
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      List<DatasetImport> imports = mapper.listByDataset(key, state, limit);
      if (imports.isEmpty()) {
        // check if dataset even exists
        if (session.getMapper(DatasetMapper.class).exists(key) == null) {
          throw NotFoundException.keyNotFound(Dataset.class, key);
        }
      }
      return imports;
    }
  }

  public ResultPage<DatasetImport> list(Collection<ImportState> states, Page page) {
    try (SqlSession session = factory.openSession(true)){
      DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
      return new ResultPage<>(page, mapper.count(states), mapper.list(states, page));
    }
  }

  /**
   * Create a new downloading dataset import with the next attempt
   */
  public DatasetImport createDownloading(Dataset d) {
    // build new import
    DatasetImport di = new DatasetImport();
    di.setDatasetKey(d.getKey());
    di.setState(ImportState.DOWNLOADING);
    di.setDownloadUri(d.getDataAccess());
    di.setStarted(LocalDateTime.now());

    try (SqlSession session = factory.openSession(true)){
      session.getMapper(DatasetImportMapper.class).create(di);
    }

    return di;
  }

  /**
   * Updates a running dataset import instance with metrics and success state.
   * Updates the dataset to point to the imports attempt.
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
      m.setDownloadUri(di.getDownloadUri());
      m.setDownload(di.getDownload());
      m.setFinished(LocalDateTime.now());
      m.setState(ImportState.FINISHED);
      m.setError(null);
      update(m, mapper);

      session.getMapper(DatasetMapper.class).updateLastImport(di.getDatasetKey(), di.getAttempt());
    }
  }

  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public void updateImportCancelled(DatasetImport di) {
    di.setFinished(LocalDateTime.now());
    di.setState(ImportState.CANCELED);
    di.setError(null);
    update(di);
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
