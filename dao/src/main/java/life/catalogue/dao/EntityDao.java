package life.catalogue.dao;

import life.catalogue.api.model.Entity;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.GlobalPageable;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Generic CRUD DAO for keyed entities
 * that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 *
 * @param <K> primary key type
 * @param <T> entity type
 * @param <M> mapper type
 */
public class EntityDao<K, T extends Entity<K>, M extends CRUD<K, T>> {

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

  public SqlSessionFactory getFactory() {
    return factory;
  }

  ResultPage<T> list(Class<? extends DatasetPageable<T>> mapperClass, int datasetKey, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      DatasetPageable<T> mapper = session.getMapper(mapperClass);
      List<T> result = mapper.list(datasetKey, p);
      return new ResultPage<>(p, result, () -> mapper.count(datasetKey));
    }
  }
  
  ResultPage<T> list(Class<? extends GlobalPageable<T>> mapperClass, Page page) {
    Page p = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()) {
      GlobalPageable<T> mapper = session.getMapper(mapperClass);
      List<T> result = mapper.list(p);
      return new ResultPage<>(p, result, mapper::count);
    }
  }

  public T get(K key) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(mapperClass).get(key);
    }
  }

  public K create(T obj, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      mapper.create(obj);
      session.commit();
      if (createAfter(obj, user, mapper, session)) {
        session.commit();
      }
      return obj.getKey();
    }
  }

  /**
   * @return true if the session is not closed and should be committed
   */
  protected boolean createAfter(T obj, int user, M mapper, SqlSession session) {
    // override to do sth useful
    return true;
  }

  public int update(T obj, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
      T old = offerChangedHook ? mapper.get(obj.getKey()) : null;
      return update(obj, old, user, session, false);
    }
  }

  /**
   * Update method that takes the old version of the object so the before/after hooks can use them.
   * Useful if the old object exists already and avoids reloading it from the database as update(obj, user) does.
   */
  public int update(T obj, T old, int user, SqlSession session) {
    return update(obj, old, user, session, true);
  }

  /**
   * Update method that takes the old version of the object so the before/after hooks can use them.
   * Useful if the old object exists already and avoids reloading it from the database as update(obj, user) does.
   * @param keepSessionOpen if true prevents any early closing of the session
   */
  private int update(T obj, T old, int user, SqlSession session, boolean keepSessionOpen) {
    M mapper = session.getMapper(mapperClass);
    updateBefore(obj, old, user, mapper, session);
    int changed = mapper.update(obj);
    session.commit();
    if (updateAfter(obj, old, user, mapper, session, keepSessionOpen)) {
      session.commit();
    }
    return changed;
  }
  
  protected void updateBefore(T obj, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }

  /**
   * @return true if the session is not closed and should be committed
   */
  protected boolean updateAfter(T obj, T old, int user, M mapper, SqlSession session, boolean keepSessionOpen) {
    // override to do sth useful
    return true;
  }

  public int delete(K key, int user) {
    try (SqlSession session = factory.openSession(false)) {
      M mapper = session.getMapper(mapperClass);
  
      T old = offerChangedHook ? mapper.get(key) : null;
      deleteBefore(key, old, user, mapper, session);
      int changed = mapper.delete(key);
      session.commit();
      if (deleteAfter(key, old, user, mapper, session)) {
        session.commit();
      }
      return changed;
    }
  }
  
  protected void deleteBefore(K key, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
  }

  /**
   * @return true if the session is not closed and should be committed
   */
  protected boolean deleteAfter(K key, T old, int user, M mapper, SqlSession session) {
    // override to do sth useful
    return true;
  }
  
}
