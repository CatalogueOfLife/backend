package org.col.db.dao;

import java.util.Collections;

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

  /**
   * THIS IS NOT IMPLEMENTED YET AND
   * AWAITS FOR THE ELASTIC SEARCH IMPLEMENTATION !!!
   */
  public ResultPage<NameUsage> search(NameSearchRequest query, Page page) {
    return new ResultPage<>(page, 0, Collections.emptyList());
  }

}
