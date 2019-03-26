package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.EditorialDecision;
import org.col.db.mapper.DecisionMapper;
import org.col.es.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDao extends CrudIntDao<EditorialDecision> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionDao.class);
  
  private final NameUsageIndexService indexService;

  public DecisionDao(SqlSessionFactory factory, NameUsageIndexService indexService) {
    super(factory, DecisionMapper.class);
    this.indexService = indexService;
  }
  
  @Override
  public void create(EditorialDecision obj) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(DecisionMapper.class).create(obj);
    }
    if (obj.getSubject().getId() != null) {
      indexService.indexTaxa(obj.getDatasetKey(), obj.getSubject().getId());
    }
  }
  
  /**
   * Updates the decision in Postgres and updates the ES index for the taxon linked to the subject id.
   * If the previous version referred to a different subject id also update that taxon.
   */
  @Override
  public int update(EditorialDecision obj) {
    try (SqlSession session = factory.openSession(true)) {
      DecisionMapper mapper = session.getMapper(DecisionMapper.class);
      final EditorialDecision old = mapper.get(obj.getKey());
      int changed = mapper.update(obj);
      if (old != null && old.getSubject().getId() != null && !old.getSubject().getId().equals(obj.getSubject().getId())) {
        indexService.indexTaxa(old.getDatasetKey(), old.getSubject().getId());
      }
      if (obj.getSubject().getId() != null) {
        indexService.indexTaxa(obj.getDatasetKey(), obj.getSubject().getId());
      }
      return changed;
    }
  }
  
  @Override
  public int delete(int key) {
    try (SqlSession session = factory.openSession(true)) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      EditorialDecision obj = dm.get(key);
      int deleted = session.getMapper(DecisionMapper.class).delete(key);
      if (obj != null && obj.getSubject().getId() != null) {
        indexService.indexTaxa(obj.getDatasetKey(), obj.getSubject().getId());
      }
      return deleted;
    }
  }
  
}
