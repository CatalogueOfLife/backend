package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.db.CRUD;
import life.catalogue.db.DatasetPageable;

import java.util.UUID;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Validator;

public class DatasetStringEntityDao<T extends DatasetScopedEntity<String>, M extends CRUD<DSID<String>, T> & DatasetPageable<T>>
  extends DatasetEntityDao<String, T, M> {

  public DatasetStringEntityDao(boolean offerChangedHook, SqlSessionFactory factory, Class<T> entityClass, Class<M> mapperClass, Validator validator) {
    super(offerChangedHook, factory, entityClass, mapperClass, validator);
  }
  
  @Override
  public DSID<String> create(T obj, int user) {
    // provide new key
    if (obj.getId() == null) {
      obj.setId(UUID.randomUUID().toString());
    }
    return super.create(obj, user);
  }

}
