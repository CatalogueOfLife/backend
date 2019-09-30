package org.col.release;

import java.util.function.Consumer;

import org.apache.ibatis.session.*;
import org.col.db.mapper.DatasetCopyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TableCopyHandler<T> extends TableCopyHandlerBase<T> {
  
  private static final Logger LOG = LoggerFactory.getLogger(TableCopyHandler.class);
  private final DatasetCopyMapper<T> mapper;
  
  public TableCopyHandler(SqlSessionFactory factory, String entityName, Class<? extends DatasetCopyMapper<T>> mapperClass, Consumer<T> updater) {
    super(factory, entityName, updater);
    mapper = session.getMapper(mapperClass);
  }
  
  @Override
  void create(T obj) {
    mapper.create(obj);
  }
}
