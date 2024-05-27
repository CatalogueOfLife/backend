package life.catalogue.common.io;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.*;

public class CompressionUtilTest {

  @Test
  public void unzipFile() throws IOException {
    for (String fn : List.of("bdj.archive", "mac.zip", "single.gz", "single.zip", "subdir.tar", "subdir.tgz", "subdir.zip")) {
      try (var dir = TempFile.directory();
           var arch = new TempFile()) {
        try (OutputStream out = Files.newOutputStream(arch.file.toPath())) {
          IOUtils.copy(Resources.stream("archives/"+fn), out);
        }
        var rf = Resources.toFile("archives/"+fn);
        var resuls = CompressionUtil.decompressFile(dir.file, rf);
        if (fn.startsWith("subdir")) {
          boolean found = false;
          for (var f : resuls) {
            if (f.isDirectory()) {
              found = true;
              break;
            }
          }
          assertTrue(found);
        }
      }
    }
  }
}