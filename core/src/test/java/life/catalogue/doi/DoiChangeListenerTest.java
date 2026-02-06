package life.catalogue.doi;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DoiChangeListenerTest {

  @Test
  void freeStoreFile() throws IOException {
    var dir = new File("/tmp/DoiChangeListener");
    FileUtils.deleteDirectory(dir);
    var f1 = DoiChangeListener.freeStoreFile(dir);
    f1.createNewFile();
    var f2 = DoiChangeListener.freeStoreFile(dir);
    assertNotEquals(f1, f2);
    assertTrue(f1.exists());
    assertEquals(new File(dir, "event-1"), f1);
    assertFalse(f2.exists());
    assertEquals(new File(dir, "event-2"), f2);
    FileUtils.deleteDirectory(dir);
  }
}