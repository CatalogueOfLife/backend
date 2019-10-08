package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.model.SpeciesEstimate;
import org.col.db.mapper.EstimateMapper;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EstimateDao extends EntityDao<Integer, SpeciesEstimate, EstimateMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateDao.class);
  

  public EstimateDao(SqlSessionFactory factory) {
    super(true, factory, EstimateMapper.class);
  }
  
  public ResultPage<SpeciesEstimate> list(int datasetKey, Page page) {
    return super.list(mapperClass, datasetKey, page);
  }
  
  public ResultPage<SpeciesEstimate> search(Rank rank, Integer min, Integer max, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      EstimateMapper mapper = session.getMapper(mapperClass);
      List<SpeciesEstimate> result = mapper.search(rank, min, max, p);
      return new ResultPage<>(p, result, () -> mapper.searchCount(rank, min, max));
    }
  }
}
