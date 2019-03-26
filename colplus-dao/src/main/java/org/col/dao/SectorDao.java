package org.col.dao;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Sector;
import org.col.db.mapper.SectorMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorDao extends CrudIntDao<Sector> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorDao.class);
  
  public SectorDao(SqlSessionFactory factory) {
    super(factory, SectorMapper.class);
  }
  
  
}
