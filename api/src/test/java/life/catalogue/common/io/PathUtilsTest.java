package life.catalogue.common.io;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.Test;

import com.sun.management.UnixOperatingSystemMXBean;

import static org.junit.Assert.*;

public class PathUtilsTest {

  private Path folderWithFiles() throws Exception {
    Path dir = Files.createTempDirectory("pathutils");
    dir.toFile().deleteOnExit();
    for (int i = 0; i < 3; i++) {
      Path f = dir.resolve("file" + i + ".txt");
      Files.writeString(f, "content");
      f.toFile().deleteOnExit();
    }
    Files.createDirectory(dir.resolve("subdir"));
    return dir;
  }

  @Test
  public void listFiles() throws Exception {
    Path dir = folderWithFiles();
    assertEquals(3, count(PathUtils.listFiles(dir, null)));
    assertEquals(3, count(PathUtils.listFiles(dir, Set.of("txt"))));
    assertEquals(0, count(PathUtils.listFiles(dir, Set.of("csv"))));
    // non existing and null folders are empty, not an error
    assertEquals(0, count(PathUtils.listFiles(dir.resolve("nope"), null)));
    assertEquals(0, count(PathUtils.listFiles(null, null)));
  }

  private int count(Iterable<Path> paths) {
    int i = 0;
    for (Path p : paths) i++;
    return i;
  }

  /**
   * https://github.com/CatalogueOfLife/backend/issues/1547
   * listFiles used to hand out an unclosed DirectoryStream, so every caller iterating it
   * with a for-each leaked the directory handle until GC.
   */
  @Test
  public void listFilesDoesNotLeakDirectoryHandles() throws Exception {
    var os = (UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    Path dir = folderWithFiles();
    // warm up so lazy class loading does not count towards the delta
    for (int i = 0; i < 5; i++) {
      count(PathUtils.listFiles(dir, null));
    }

    long before = os.getOpenFileDescriptorCount();
    final int runs = 100;
    for (int i = 0; i < runs; i++) {
      count(PathUtils.listFiles(dir, null));
    }
    long leaked = os.getOpenFileDescriptorCount() - before;
    assertTrue("leaked " + leaked + " directory handles in " + runs + " listings", leaked < 10);
  }
}
