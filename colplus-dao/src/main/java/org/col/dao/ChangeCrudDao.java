package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.IntKey;
import org.col.db.CRUDInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic CRUD DAO that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 */
public class ChangeCrudDao<T extends IntKey, M extends CRUDInt<T>> extends CrudIntDao<T> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ChangeCrudDao.class);
  private Class<M> mapperClass;
  
  public ChangeCrudDao(SqlSessionFactory factory, Class<M> mapperClass) {
    super(factory , mapperClass);
    this.mapperClass = mapperClass;
  }
  
  @Override
  public void create(T obj) {
    try (SqlSession session = factory.openSession(true)) {
      M mapper = session.getMapper(mapperClass);
      mapper.create(obj);
      postCreate(obj, mapper, session);
    }
  }
  
  protected void postCreate(T obj, M mapper, SqlSession session) {
    // override to do sth useful
  }

  /**
   * Updates the decision in Postgres and updates the ES index for the taxon linked to the subject id.
   * If the previous version referred to a different subject id also update that taxon.
   */
  @Override
  public int update(T obj) {
    try (SqlSession session = factory.openSession(true)) {
      M mapper = session.getMapper(mapperClass);
      T old = mapper.get(obj.getKey());
      int res = session.getMapper(mapperClass).update(obj);
      postUpdate(obj, old, mapper, session);
      return res;
    }
  }
  
  protected void postUpdate(T obj, T old, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  @Override
  public int delete(int key) {
    try (SqlSession session = factory.openSession(true)) {
      M mapper = session.getMapper(mapperClass);
      T old = mapper.get(key);
      int res = session.getMapper(mapperClass).delete(key);
      postDelete(key, old, mapper, session);
      return res;
    }
  }
  
  protected void postDelete(int key, T old, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
}
