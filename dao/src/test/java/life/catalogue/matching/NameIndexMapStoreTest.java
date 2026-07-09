package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndexMapStore;
import life.catalogue.matching.nidx.NameIndexStore;

import java.io.IOException;

public class NameIndexMapStoreTest extends NameIndexStoreTest {

  @Override
  NameIndexStore create() throws IOException {
    return new NameIndexMapStore();
  }
}
