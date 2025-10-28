package life.catalogue.matching;

import life.catalogue.common.io.TempFile;

import java.io.IOException;
import java.util.List;

import org.junit.After;

public class UsageMatcherChronicleStoreTest extends UsageMatcherStoreTestBase {
  TempFile dbFile;

  @Override
  UsageMatcherStore createStore(int datasetKey) throws IOException {
    dbFile = TempFile.directory();
    return UsageMatcherChronicleStore.build(datasetKey, dbFile.file, 1000, List.of(
      UsageMatcherChronicleStore.sample("DRFTGZH"),
      UsageMatcherChronicleStore.sample("3456G"),
      UsageMatcherChronicleStore.sample("$E$%FGZHZGU"),
      UsageMatcherChronicleStore.sample("http://urn:lsid.org/urn:lsid:ipni.org:names:77166011-1")
    ));
  }

  @After
  public void destroy() {
    dbFile.close();
  }
}