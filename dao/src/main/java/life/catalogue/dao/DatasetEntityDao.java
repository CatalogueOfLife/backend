package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;

import java.util.UUID;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic CRUD DAO for dataset scoped entities
 * that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 */
public class DatasetEntityDao<K, T extends DatasetScopedEntity<K>, M extends CRUD<DSID<K>, T> & DatasetPageable<T>> extends DataEntityDao<DSID<K>, T, M> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetEntityDao.class);
  
  public DatasetEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<M> mapperClass) {
    super(offerChangedHook, factory, mapperClass);
  }
  
  public ResultPage<T> list(int datasetKey, Page page) {
    return super.list(mapperClass, datasetKey, page);
  }
  
  public static <T extends VerbatimEntity & DSID<String>> T newKey(T obj) {
    obj.setVerbatimKey(null);
    obj.setId(UUID.randomUUID().toString());
    return obj;
  }
  
}
