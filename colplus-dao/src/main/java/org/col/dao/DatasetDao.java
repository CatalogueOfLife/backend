package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.Dataset;
import org.col.api.DatasetImport;
import org.col.api.Page;
import org.col.api.PagingResultSet;
import org.col.db.mapper.DatasetImportMapper;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.List;

public class DatasetDao {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(DatasetDao.class);

	private final SqlSession session;
  private final DatasetMapper mapper;

	public DatasetDao(SqlSession sqlSession) {
		this.session = sqlSession;
    mapper = session.getMapper(DatasetMapper.class);
	}

	public Dataset get(int key) {
    return mapper.get(key);
  }

	public PagingResultSet<Dataset> search(String q, @Nullable Page page) {
		page = page == null ? new Page() : page;
		String query = q + ":*"; // Enable "starts_with" term matching
		int total = mapper.countSearchResults(query);
		List<Dataset> result = mapper.search(query, page);
		return new PagingResultSet<>(page, total, result);
	}

	public Iterable<Dataset> all(){
    return new Pager<Dataset>(100, mapper::list);
  }

  /**
   * Creates a new successful dataset import instance with metrics
   */
  public DatasetImport createImportSuccess(Dataset dataset,
                                           LocalDateTime importStart,
                                           LocalDateTime download
  ) {
    LOG.info("Create new metrics for dataset {}", dataset.getKey());
    DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
    // metrics counts
    DatasetImport di = mapper.metrics(dataset.getKey());
    // update new metrics instance with more infos
    di.setDatasetKey(dataset.getKey());
    di.setDownload(download);
    di.setStarted(importStart);
    di.setFinished(LocalDateTime.now());
    di.setSuccess(true);
    di.setError(null);
    mapper.create(di);
    return di;
  }

  /**
   * Creates a new dataset import instance without metrics for a failed import.
   */
  public DatasetImport createImportFailure(Dataset dataset,
                                           LocalDateTime importStart,
                                           LocalDateTime download,
                                           Exception e
  ) {
    LOG.info("Create new import error log for dataset {}", dataset.getKey());
    DatasetImportMapper mapper = session.getMapper(DatasetImportMapper.class);
    // metrics counts
    DatasetImport di = new DatasetImport();
    // update new metrics instance with more infos
    di.setDatasetKey(dataset.getKey());
    di.setDownload(download);
    di.setStarted(importStart);
    di.setFinished(LocalDateTime.now());
    di.setSuccess(false);
    di.setError(e.getMessage());
    mapper.create(di);
    return di;
  }

}
