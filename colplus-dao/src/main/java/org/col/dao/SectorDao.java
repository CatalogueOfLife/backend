package org.col.dao;

import java.util.Objects;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Sector;
import org.col.api.model.Taxon;
import org.col.api.vocab.Datasets;
import org.col.db.mapper.SectorMapper;
import org.col.db.mapper.TaxonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectorDao extends ChangeCrudDao<Sector, SectorMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(SectorDao.class);
  
  public SectorDao(SqlSessionFactory factory) {
    super(factory, SectorMapper.class);
  }
  
  @Override
  protected void postCreate(Sector obj, SectorMapper mapper, SqlSession session) {
    incCounts(session, obj, 1);
  }
  
  @Override
  protected void postUpdate(Sector obj, Sector old, SectorMapper mapper, SqlSession session) {
    if (old.getTarget() == null || obj.getTarget() == null || !Objects.equals(old.getTarget().getId(), obj.getTarget().getId())) {
      incCounts(session, obj, 1);
      incCounts(session, old, -1);
    }
  }
  
  @Override
  protected void postDelete(int key, Sector old, SectorMapper mapper, SqlSession session) {
    incCounts(session, old, -1);
  }
  
  private void incCounts(SqlSession session, Sector s, int delta) {
    if (s != null && s.getTarget() != null) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      
      tm.incDatasetSectorCount(Datasets.DRAFT_COL, s.getTarget().getId(), s.getDatasetKey(), delta);
      // also modify counts for all parents
      for (Taxon t : tm.classification(Datasets.DRAFT_COL, s.getTarget().getId())) {
        tm.incDatasetSectorCount(Datasets.DRAFT_COL, t.getId(), s.getDatasetKey(), delta);
      }
    }
  }
}
