package org.col.dao;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.IntKey;
import org.col.db.CRUDInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrudIntDao<T extends IntKey> implements CRUDInt<T> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(CrudIntDao.class);
  protected final SqlSessionFactory factory;
  protected final Class<? extends CRUDInt<T>> mapperClass;
  
  public CrudIntDao(SqlSessionFactory factory, Class<? extends CRUDInt<T>> mapperClass) {
    this.factory = factory;
    this.mapperClass = mapperClass;
  }
  
  @Override
  public T get(int key) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(mapperClass).get(key);
    }
  }
  
  @Override
  public T get(Integer key) {
    return get((int)key);
  }
  
  @Override
  public void create(T obj) {
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(mapperClass).create(obj);
    }
  }
  
  /**
   * Updates the decision in Postgres and updates the ES index for the taxon linked to the subject id.
   * If the previous version referred to a different subject id also update that taxon.
   */
  @Override
  public int update(T obj) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(mapperClass).update(obj);
    }
  }
  
  @Override
  public int delete(int key) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(mapperClass).delete(key);
    }
  }
  
  @Override
  public int delete(Integer key) {
    return delete((int)key);
  }
}
