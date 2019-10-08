package org.col.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.DataEntity;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.db.CRUD;
import org.col.db.DatasetPageable;
import org.col.db.GlobalPageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic CRUD DAO for keyed entities
 * that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 */
public class EntityDao<K, V extends DataEntity<K>, M extends CRUD<K, V>> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(EntityDao.class);
  protected final SqlSessionFactory factory;
  protected final Class<M> mapperClass;
  private final boolean offerChangedHook;
  
  /**
   * @param offerChangedHook if true loads the old version of the updated or deleted object and offers it to the before and after methods.
   *                         If false the old value will always be null but performance will be better
   */
  public EntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<M> mapperClass) {
    this.offerChangedHook = offerChangedHook;
    this.factory = factory;
    this.mapperClass = mapperClass;
  }
  
  ResultPage<V> list(Class<? extends DatasetPageable<V>> mapperClass, int datasetKey, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      DatasetPageable<V> mapper = session.getMapper(mapperClass);
      List<V> result = mapper.list(datasetKey, p);
      return new ResultPage<>(p, result, () -> mapper.count(datasetKey));
    }
  }
  
  ResultPage<V> list(Class<? extends GlobalPageable<V>> mapperClass, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      GlobalPageable<V> mapper = session.getMapper(mapperClass);
      List<V> result = mapper.list(p);
      return new ResultPage<>(p, result, mapper::count);
    }
  }
  
  public V get(K key) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(mapperClass).get(key);
    }
  }
  
  public K create(V obj, int user) {
    obj.applyUser(user);
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      mapper.create(obj);
      session.commit();
      createAfter(obj, user, mapper, session);
      return obj.getKey();
    }
  }
  
  protected void createAfter(V obj, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  public int update(V obj, int user) {
    obj.applyUser(user);
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      V old = offerChangedHook ? mapper.get(obj.getKey()) : null;
      updateBefore(obj, old, user, mapper, session);
      int changed = mapper.update(obj);
      session.commit();
      updateAfter(obj, old, user, mapper, session);
      return changed;
    }
  }
  
  protected void updateBefore(V obj, V old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  protected void updateAfter(V obj, V old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  public int delete(K key, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
  
      V old = offerChangedHook ? mapper.get(key) : null;
      deleteBefore(key, old, user, mapper, session);
      int changed = mapper.delete(key);
      session.commit();
      deleteAfter(key, old, user, mapper, session);
      return changed;
    }
  }
  
  protected void deleteBefore(K key, V old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  protected void deleteAfter(K key, V old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
}
