package org.col.dao;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.EditorialDecision;
import org.col.db.mapper.DecisionMapper;
import org.col.es.NameUsageIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDao extends ChangeCrudDao<EditorialDecision, DecisionMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DecisionDao.class);
  
  private final NameUsageIndexService indexService;

  public DecisionDao(SqlSessionFactory factory, NameUsageIndexService indexService) {
    super(factory, DecisionMapper.class);
    this.indexService = indexService;
  }
  
  @Override
  protected void postCreate(EditorialDecision obj, DecisionMapper mapper, SqlSession session) {
    if (obj.getSubject().getId() != null) {
      indexService.indexTaxa(obj.getDatasetKey(), Lists.newArrayList(obj.getSubject().getId()));
    }
  }
  
  /**
   * Updates the decision in Postgres and updates the ES index for the taxon linked to the subject id.
   * If the previous version referred to a different subject id also update that taxon.
   */
  @Override
  protected void postUpdate(EditorialDecision obj, EditorialDecision old, DecisionMapper mapper, SqlSession session) {
    final List<String> ids = new ArrayList<>();
    if (old != null && old.getSubject().getId() != null && !old.getSubject().getId().equals(obj.getSubject().getId())) {
      ids.add(old.getSubject().getId());
    }
    if (obj.getSubject().getId() != null) {
      ids.add(obj.getSubject().getId());
    }
    indexService.indexTaxa(obj.getDatasetKey(), ids);
  }
  
  @Override
  protected void postDelete(int key, EditorialDecision old, DecisionMapper mapper, SqlSession session) {
    if (old != null && old.getSubject().getId() != null) {
      indexService.indexTaxa(old.getDatasetKey(), Lists.newArrayList(old.getSubject().getId()));
    }
  }
  
}
