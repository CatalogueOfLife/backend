package org.col.dao;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.Reference;
import org.col.db.mapper.ReferenceMapper;

public class ReferenceDao extends DatasetEntityDao<Reference, ReferenceMapper> {
  
  
  public ReferenceDao(SqlSessionFactory factory) {
    super(false, factory, ReferenceMapper.class);
  }
  
  public Reference get(int datasetKey, String id, @Nullable String page) {
    Reference ref = super.get(datasetKey, id);
    if (ref == null) {
      return null;
    }
    if (page != null) {
      ref.setPage(page);
    }
    return ref;
  }
  
}
