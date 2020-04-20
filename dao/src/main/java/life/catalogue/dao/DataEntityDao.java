package life.catalogue.dao;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import life.catalogue.api.model.DataEntity;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.GlobalPageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public DataEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<M> mapperClass) {
    super(offerChangedHook, factory, mapperClass);
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
