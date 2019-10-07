package org.col.release;

import java.util.function.Consumer;

import org.apache.ibatis.session.*;
import org.col.api.model.DataEntity;
import org.col.db.CRUD;
import org.col.db.mapper.ProcessableDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TableCopyHandler<K, T extends DataEntity<K>, M extends CRUD<K,T> & ProcessableDataset<T>> extends TableCopyHandlerBase<T> {
  
  private static final Logger LOG = LoggerFactory.getLogger(TableCopyHandler.class);
  private final M mapper;
  
  public TableCopyHandler(SqlSessionFactory factory, String entityName, Class<M> mapperClass, Consumer<T> updater) {
    super(factory, entityName, updater);
    mapper = session.getMapper(mapperClass);
  }
  
  @Override
  void create(T obj) {
    mapper.create(obj);
  }
}
