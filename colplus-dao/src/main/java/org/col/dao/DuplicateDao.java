package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.EqualityMode;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.mapper.DuplicateMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateDao {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DuplicateDao.class);
  
  private final SqlSession session;
  private final DuplicateMapper mapper;
  
  public DuplicateDao(SqlSession sqlSession) {
    this.session = sqlSession;
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  public List<Duplicate> find(int datasetKey, EqualityMode mode, Rank rank, TaxonomicStatus status1, TaxonomicStatus status2, Boolean parentDifferent, Boolean withDecision, Page page) {
      return mapper.find(datasetKey, mode, rank, status1, status2, parentDifferent, withDecision, page);
  }
}
