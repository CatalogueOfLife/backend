package org.col.db.dao;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.col.api.exception.NotFoundException;
import org.col.api.model.Dataset;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.search.DatasetSearchRequest;
import org.col.db.mapper.DatasetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    Dataset d = mapper.get(key);
    if(d == null) {
      throw NotFoundException.keyNotFound(Dataset.class, key);
    }
    return d;
  }

  public ResultPage<Dataset> search(@Nullable DatasetSearchRequest req, @Nullable Page page) {
    page = page == null ? new Page() : page;
    req = req == null ? new DatasetSearchRequest() : req;

    int total = mapper.count(req);
    List<Dataset> result = mapper.search(req, page);
    return new ResultPage<>(page, total, result);
  }

  public Integer create(Dataset dataset) {
    mapper.create(dataset);
    session.commit();
    return dataset.getKey();
  }

  public void update(Dataset dataset) {
    int i = mapper.update(dataset);
    if (i == 0) {
      throw NotFoundException.keyNotFound(Dataset.class, dataset.getKey());
    }
    session.commit();
  }

  public void delete(int key) {
    int i = mapper.delete(key);
    if (i == 0) {
      throw NotFoundException.keyNotFound(Dataset.class, key);
    }
    session.commit();
  }
}
