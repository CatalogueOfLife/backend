package org.col.db.dao;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.*;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameUsageMapper;
import org.col.db.mapper.SynonymMapper;
import org.col.db.mapper.TaxonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Collection of methods dealing with name usages, i.e. a name in the context of either a Taxon, Synonym or BareName.
 * Mostly exposed by searches.
 */
public class NameUsageDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDao.class);

  private final SqlSession session;
  private final TaxonMapper tMapper;
  private final SynonymMapper sMapper;
  private final NameUsageMapper mapper;

  public NameUsageDao(SqlSession sqlSession) {
    this.session = sqlSession;
    mapper = session.getMapper(NameUsageMapper.class);
    tMapper = session.getMapper(TaxonMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
  }

  public ResultPage<NameUsage> search(NameSearch query, Page page) {
    if (query.isEmpty()) {
      // default to order by key for large, unfiltered resultssets
      query.setSortBy(NameSearch.SortBy.KEY);
    } else if (query.getSortBy() == null) {
      query.setSortBy(NameSearch.SortBy.NAME);
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
