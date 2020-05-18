package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class TableCopyHandlerWithKeyMap<T extends DatasetScopedEntity<Integer>, M extends Create<T> & DatasetProcessable<T>> extends TableCopyHandlerBase<T> {

  private static final Logger LOG = LoggerFactory.getLogger(TableCopyHandlerWithKeyMap.class);
  private final M mapper;
  private final Int2IntMap keyMap;

  public TableCopyHandlerWithKeyMap(SqlSessionFactory factory, String entityName, Class<M> mapperClass, Consumer<T> updater) {
    super(factory, entityName, updater);
    mapper = session.getMapper(mapperClass);
    keyMap = new Int2IntOpenHashMap();
  }
  
  @Override
  void create(T obj) {
    mapper.create(obj);
  }

  public Int2IntMap getKeyMap() {
    return keyMap;
  }
}
