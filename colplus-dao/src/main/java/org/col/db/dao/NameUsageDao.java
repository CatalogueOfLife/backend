package org.col.db.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.NameSearchRequest;
import org.col.api.model.NameUsage;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.db.mapper.NameUsageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collection of methods dealing with name usages, i.e. a name in the context of either a Taxon, Synonym or BareName.
 * Mostly exposed by searches.
 */
public class NameUsageDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDao.class);

  private final SqlSession session;
  private final NameUsageMapper mapper;

  public NameUsageDao(SqlSession sqlSession) {
    this.session = sqlSession;
    mapper = session.getMapper(NameUsageMapper.class);
  }

  public ResultPage<NameUsage> search(NameSearchRequest query, Page page) {
    if (query.isEmpty()) {
      // default to order by key for large, unfiltered resultsets
      query.setSortBy(NameSearchRequest.SortBy.KEY);
    } else if (query.getSortBy() == null) {
      query.setSortBy(NameSearchRequest.SortBy.NAME);
    }
    if (query.getQ() != null) {
      query.setQ(query.getQ() + ":*");
    }
    int total = 0;
    List<NameUsage> hits = mapper.search(query, page);
    if (!hits.isEmpty()) {
      total = mapper.searchCount(query);
    }
    return new ResultPage<>(page, total, hits);
  }

}
