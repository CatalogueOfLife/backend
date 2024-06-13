package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndexChronicleStore;
import life.catalogue.matching.nidx.NameIndexStore;

import java.io.IOException;

public class NameIndexChronicleStoreTest extends NameIndexStoreTest {

  @Override
  NameIndexStore create() throws IOException {
    return new NameIndexChronicleStore(cfg);
  }
}