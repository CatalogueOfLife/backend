package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.db.Create;
import life.catalogue.db.DatasetProcessable;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.ArrayList;
import java.util.List;

public class TableCopyHandlerWithKeyMap<T extends DatasetScopedEntity<Integer>, M extends Create<T> & DatasetProcessable<T>>
  extends TableCopyHandler<T, M> {

  private final Int2IntMap keyMap;
  private final List<OldKeyWrapper> batch = new ArrayList<>(TableCopyHandlerBase.BATCHSIZE);

  class OldKeyWrapper {
    final int oldKey;
    final T obj;

    OldKeyWrapper(T obj) {
      this.obj = obj;
      oldKey = obj.getId();
    }
  }

  public TableCopyHandlerWithKeyMap(int datasetKey, SqlSessionFactory factory, String entityName, Class<M> mapperClass) {
    super(datasetKey, factory, entityName, mapperClass);
    keyMap = new Int2IntOpenHashMap();
  }

  @Override
  public void accept(T obj) {
    batch.add(new OldKeyWrapper(obj));
    super.accept(obj);
  }

  @Override
  void commitBatch() {
    super.commitBatch();
    // only now we have new keys!
    for (OldKeyWrapper ow : batch) {
      keyMap.put(ow.oldKey, (int) ow.obj.getId());
    }
    batch.clear();
  }

  public Int2IntMap getKeyMap() {
    return keyMap;
  }
}
