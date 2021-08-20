package life.catalogue.dao;

import life.catalogue.api.model.DataEntity;
import life.catalogue.db.CRUD;

import org.apache.ibatis.session.SqlSessionFactory;

import javax.validation.Validator;

/**
 * Generic CRUD Data DAO for keyed entities
 * that manages created/mpdified by properties.
 *
 * @param <K> primary key type
 * @param <T> entity type
 * @param <M> mapper type
 */
public class DataEntityDao<K, T extends DataEntity<K>, M extends CRUD<K, T>> extends EntityDao<K, T, M> {

  /**
   * @param offerChangedHook if true loads the old version of the updated or deleted object and offers it to the before and after methods.
   *                         If false the old value will always be null but performance will be better
   */
  public DataEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<T> entityClass, Class<M> mapperClass, Validator validator) {
    super(offerChangedHook, factory, entityClass, mapperClass, validator);
  }

  
  @Override
  public K create(T obj, int user) {
    obj.applyUser(user);
    return super.create(obj, user);
  }

  @Override
  public int update(T obj, int user) {
    obj.applyUser(user);
    return super.update(obj, user);
  }
  
}
