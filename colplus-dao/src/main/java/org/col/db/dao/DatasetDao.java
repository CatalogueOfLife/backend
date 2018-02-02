package org.col.db.dao;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.db.KeyNotFoundException;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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
    Dataset result = mapper.get(key);
    if (result == null) {
      throw new KeyNotFoundException(Dataset.class, key);
    }
    return result;
  }

  public ResultPage<Dataset> search(String q, @Nullable Page page) {
    page = page == null ? new Page() : page;
    // String query = q + ":*"; // Enable "starts_with" term matching
    int total = mapper.count(q);
    List<Dataset> result = mapper.search(q, page);
    return new ResultPage<>(page, total, result);
  }

}
