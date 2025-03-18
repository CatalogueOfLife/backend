package life.catalogue.matching;

import life.catalogue.common.io.TempFile;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MatchingStorageChrononicleTest {

  @Test
  void create() throws Exception {
    try (TempFile tf = new TempFile()) {
      var metadata = new MatchingStorageMetadata();
      metadata.setNumUsages(300);
      metadata.setNumNidx(200);
      metadata.setNumCanonicals(100);
      var storage = MatchingStorageChrononicle.create(tf.file, 16, metadata);
      assertNotNull(storage);
    }
  }
}