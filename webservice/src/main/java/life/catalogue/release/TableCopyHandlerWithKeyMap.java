package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.function.Consumer;

public class TableCopyHandlerWithKeyMap<T extends DatasetScopedEntity<Integer>, M extends Create<T> & DatasetProcessable<T>>
  extends TableCopyHandler<T, M> {

  private final Int2IntMap keyMap;

  public TableCopyHandlerWithKeyMap(SqlSessionFactory factory, String entityName, Class<M> mapperClass, Consumer<T> updater) {
    super(factory, entityName, mapperClass, updater);
    keyMap = new Int2IntOpenHashMap();
  }

  @Override
  public void accept(T obj) {
    int oldKey = obj.getId();
    super.accept(obj);
    keyMap.put(oldKey, (int) obj.getId());
  }

  public Int2IntMap getKeyMap() {
    return keyMap;
  }
}
