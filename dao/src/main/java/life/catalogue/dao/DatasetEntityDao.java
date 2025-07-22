package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;
import life.catalogue.db.DatasetProcessable;

import java.util.UUID;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Validator;

/**
 * Generic CRUD DAO for dataset scoped entities
 * that allows to hook post actions for create, update and delete
 * with access to the old version of the object.
 */
public class DatasetEntityDao<K, T extends DatasetScopedEntity<K>, M extends CRUD<DSID<K>, T> & DatasetPageable<T> & DatasetProcessable<T>> extends DataEntityDao<DSID<K>, T, M> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetEntityDao.class);
  public DatasetEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<T> entityClass, Class<M> mapperClass, Validator validator) {
    super(offerChangedHook, factory, entityClass, mapperClass, validator);
  }

  public ResultPage<T> list(int datasetKey, Page page) {
    return super.list(mapperClass, datasetKey, page);
  }
  
  public static <T extends VerbatimEntity & DSID<String>> T newKey(T obj) {
    obj.setVerbatimKey(null);
    obj.setId(UUID.randomUUID().toString());
    return obj;
  }

  public void deleteByDataset(int datasetKey){
    try (SqlSession session = factory.openSession(true)) {
      M mapper = session.getMapper(mapperClass);
      mapper.deleteByDataset(datasetKey);
    }
  }

  public int countByDataset(int datasetKey){
    try (SqlSession session = factory.openSession(true)) {
      M mapper = session.getMapper(mapperClass);
      return mapper.count(datasetKey);
    }
  }

}
