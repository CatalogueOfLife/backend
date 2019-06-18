package org.col.dao;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.SpeciesEstimate;
import org.col.db.mapper.EstimateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EstimateDao extends GlobalEntityDao<SpeciesEstimate, EstimateMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EstimateDao.class);
  

  public EstimateDao(SqlSessionFactory factory) {
    super(true, factory, EstimateMapper.class);
  }
  
}
