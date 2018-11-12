package org.col.db.dao;

import java.util.List;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
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
  
  public ResultPage<Dataset> search(@Nullable DatasetSearchRequest req, @Nullable Page page) {
    page = page == null ? new Page() : page;
    req = req == null || req.isEmpty() ? new DatasetSearchRequest() : req;
    if (req.getSortBy() == null) {
      if (!StringUtils.isBlank(req.getQ())) {
        req.setSortBy(DatasetSearchRequest.SortBy.RELEVANCE);
      } else {
        req.setSortBy(DatasetSearchRequest.SortBy.KEY);
      }
    } else if (req.getSortBy() == DatasetSearchRequest.SortBy.RELEVANCE && StringUtils.isBlank(req.getQ())) {
      req.setQ(null);
      req.setSortBy(DatasetSearchRequest.SortBy.KEY);
    }
    int total = mapper.count(req);
    List<Dataset> result = mapper.search(req, page);
    return new ResultPage<>(page, total, result);
  }
  
}
