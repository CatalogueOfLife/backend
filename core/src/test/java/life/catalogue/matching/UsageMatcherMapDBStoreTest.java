package life.catalogue.matching;

import life.catalogue.common.io.TempFile;

import java.io.IOException;

import org.junit.After;
import org.mapdb.DBMaker;

public class UsageMatcherMapDBStoreTest extends UsageMatcherStoreTestBase {
  TempFile dbFile;

  @Override
  UsageMatcherStore createStore(int datasetKey) throws IOException {
    dbFile = TempFile.file();
    DBMaker.Maker maker = DBMaker
      .fileDB(dbFile.file)
      .fileMmapEnableIfSupported();

    var dbMaker = DBMaker.memoryDB();
    return UsageMatcherMapDBStore.build(datasetKey, dbMaker);
  }

  @After
  public void destroy() {
    dbFile.close();
  }
}