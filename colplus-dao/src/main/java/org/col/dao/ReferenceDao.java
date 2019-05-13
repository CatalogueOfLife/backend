package org.col.dao;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.DatasetID;
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
  
  /**
   * Copies the given nam instance, modifying the original and assigning a new id
   */
  public static DatasetID copyReference(final SqlSession session, final Reference r, final int targetDatasetKey, int user) {
    final DatasetID orig = new DatasetID(r);
    newKey(r);
    r.applyUser(user, true);
    r.setDatasetKey(targetDatasetKey);
    session.getMapper(ReferenceMapper.class).create(r);
    return orig;
  }
}
