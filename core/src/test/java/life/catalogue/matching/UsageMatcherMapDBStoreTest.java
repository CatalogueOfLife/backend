package life.catalogue.matching;

import org.mapdb.DBMaker;

public class UsageMatcherMapDBStoreTest extends UsageMatcherStoreTestBase {

  @Override
  UsageMatcherStore createStore(int datasetKey) {
    var dbMaker = DBMaker.memoryDB();
    return UsageMatcherMapDBStore.build(datasetKey, dbMaker);
  }

}