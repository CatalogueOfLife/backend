package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.function.Consumer;

public class TableCopyHandler<T extends DSID<?>, M extends Create<T> & DatasetProcessable<T>> extends TableCopyHandlerBase<T> {
  
  private final int datasetKey;
  private final M mapper;

  
  public TableCopyHandler(int datasetKey, SqlSessionFactory factory, String entityName, Class<M> mapperClass, Consumer<T> updater) {
    super(factory, entityName, updater);
    mapper = session.getMapper(mapperClass);
    this.datasetKey = datasetKey;
  }
  
  @Override
  void create(T obj) {
    obj.setDatasetKey(datasetKey);
    mapper.create(obj);
  }
}
