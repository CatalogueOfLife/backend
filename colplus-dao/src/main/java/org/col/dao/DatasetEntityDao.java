package org.col.dao;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.*;
import org.col.db.mapper.DatasetCRUDMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic CRUD DAO for dataset scoped entities
 * that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 */
public class DatasetEntityDao<T extends DatasetEntity & UserManaged, M extends DatasetCRUDMapper<T>> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetEntityDao.class);
  protected final SqlSessionFactory factory;
  protected final Class<M> mapperClass;
  private final boolean offerChangedHook;
  
  /**
   * @param offerChangedHook if true loads the old version of the updated or deleted object and offers it to the before and after methods.
   *                         If false the old value will always be null but performance will be better
   */
  public DatasetEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<M> mapperClass) {
    this.offerChangedHook = offerChangedHook;
    this.factory = factory;
    this.mapperClass = mapperClass;
  }
  
  public T get(int datasetKey, String key) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(mapperClass).get(datasetKey, key);
    }
  }
  
  public ResultPage<T> list(int datasetKey, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      M mapper = session.getMapper(mapperClass);
      List<T> result = mapper.list(datasetKey, p);
      int total = result.size() == p.getLimit() ? mapper.count(datasetKey) : result.size();
      return new ResultPage<>(p, total, result);
    }
  }
  
  
  public String create(T obj, int user) {
    obj.applyUser(user);
    if (obj.getId() == null) {
      // create random id for new object
      obj.setId(UUID.randomUUID().toString());
    }
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      mapper.create(obj);
      createAfter(obj, user, mapper, session);
      session.commit();
      return obj.getId();
    }
  }
  
  void createAfter(T obj, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  public int update(T obj, int user) {
    obj.applyUser(user);
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      T old = offerChangedHook ? mapper.get(obj.getDatasetKey(), obj.getId()) : null;
      updateBefore(obj, old, user, mapper, session);
      int changed = mapper.update(obj);
      updateAfter(obj, old, user, mapper, session);
      session.commit();
      return changed;
    }
  }
  
  protected void updateBefore(T obj, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  protected void updateAfter(T obj, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  public int delete(int datasetKey, String id, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      T old = offerChangedHook ? mapper.get(datasetKey, id) : null;
      deleteBefore(datasetKey, id, old, user, mapper, session);
      int changed = mapper.delete(datasetKey, id);
      deleteAfter(datasetKey, id, old, user, mapper, session);
      session.commit();
      return changed;
    }
  }
  
  protected void deleteBefore(int datasetKey, String id, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  protected void deleteAfter(int datasetKey, String id, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }
  
  public static <T extends VerbatimEntity & DatasetEntity> T newKey(T e) {
    e.setVerbatimKey(null);
    e.setId(UUID.randomUUID().toString());
    return e;
  }
  
}
