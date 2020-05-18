package life.catalogue.release;

import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class TableCopyHandler<T, M extends Create<T> & DatasetProcessable<T>> extends TableCopyHandlerBase<T> {
  
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
