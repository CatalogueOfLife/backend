package life.catalogue.common.io;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

        switch (fn){
          case "single.gz":
            assertEquals(fn, 1, resuls.size());
            assertEquals(fn, "single", resuls.get(0).getName());
            break;
          case "single.zip":
            assertEquals(fn, 1, resuls.size());
            assertEquals(fn, "taxa.csv", resuls.get(0).getName());
            break;
          default:
            assertTrue(fn, resuls.size() > 1);
        }

        if (fn.startsWith("subdir")) {
          var names = resuls.stream().map(this::extractBaseName).collect(Collectors.toSet());
          assertTrue(fn, names.size() > 1);
          assertTrue(fn, names.contains("treatments/Hind2013.txt"));
        }
      }
    }
  }

  String extractBaseName(File f) {
    Pattern p = Pattern.compile("/tmp/col/[0-9a-f-]+/");
    return p.matcher(f.getAbsolutePath()).replaceFirst("");
  }
}