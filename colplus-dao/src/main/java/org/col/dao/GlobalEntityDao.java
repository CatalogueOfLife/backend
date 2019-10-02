package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.GlobalEntity;
import org.col.db.GlobalCRUD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic CRUD DAO for globally scoped entities with integer keys
 * that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 */
public class GlobalEntityDao<T extends GlobalEntity, M extends GlobalCRUD<T>> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(GlobalEntityDao.class);
  protected final SqlSessionFactory factory;
  protected final Class<M> mapperClass;
  private final boolean offerChangedHook;
  
  /**
   * @param offerChangedHook if true loads the old version of the updated or deleted object and offers it to the before and after methods.
   *                         If false the old value will always be null but performance will be better
   */
  public GlobalEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<M> mapperClass) {
    this.offerChangedHook = offerChangedHook;
    this.factory = factory;
    this.mapperClass = mapperClass;
  }
  
  public T get(Integer key) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(mapperClass).get(key);
    }
  }
  
  public Integer create(T obj, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      mapper.create(obj);
      session.commit();
      createAfter(obj, user, mapper, session);
      return obj.getKey();
    }
  }
  
  protected void createAfter(T obj, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  public int update(T obj, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      T old = offerChangedHook ? mapper.get(obj.getKey()) : null;
      updateBefore(obj, old, user, mapper, session);
      int changed = mapper.update(obj);
      session.commit();
      updateAfter(obj, old, user, mapper, session);
      return changed;
    }
  }
  
  protected void updateBefore(T obj, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  protected void updateAfter(T obj, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  public int delete(Integer key, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
  
      T old = offerChangedHook ? mapper.get(key) : null;
      deleteBefore(key, old, user, mapper, session);
      int changed = mapper.delete(key);
      session.commit();
      deleteAfter(key, old, user, mapper, session);
      return changed;
    }
  }
  
  protected void deleteBefore(Integer key, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  protected void deleteAfter(Integer key, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
}
