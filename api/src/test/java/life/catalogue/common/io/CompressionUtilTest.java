package life.catalogue.common.io;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CompressionUtilTest {

  /**
   * The parallel writer deflates entries on several threads and emits them in completion order, so it must
   * still produce an archive with exactly the same entries and content as the single threaded one.
   */
  @Test
  public void zipDirParallel() throws IOException {
    try (var src = TempFile.directory()) {
      // enough files and enough bytes that more than one thread actually gets work
      for (int i = 0; i < 25; i++) {
        File f = new File(src.file, "sub" + (i % 3) + "/file" + i + ".tsv");
        f.getParentFile().mkdirs();
        Files.writeString(f.toPath(), ("col:ID\tcol:name\n" + i + "\tAbies alba " + i + "\n").repeat(500));
      }

      try (var seqZip = new TempFile(); var parZip = new TempFile();
           var seqDir = TempFile.directory(); var parDir = TempFile.directory()) {
        CompressionUtil.zipDir(src.file, seqZip.file, true, 6, 1);
        CompressionUtil.zipDir(src.file, parZip.file, true, 6, 4);

        CompressionUtil.decompressFile(seqDir.file, seqZip.file);
        CompressionUtil.decompressFile(parDir.file, parZip.file);
        assertEquals(relativeContents(seqDir.file), relativeContents(parDir.file));
        // and it really did unpack the 25 files, not an empty archive compared to another empty one
        assertEquals(25, relativeContents(parDir.file).size());
      }
    }
  }

  private static Map<String, String> relativeContents(File dir) throws IOException {
    Map<String, String> byPath = new TreeMap<>();
    try (var paths = Files.walk(dir.toPath())) {
      for (var p : paths.filter(Files::isRegularFile).collect(Collectors.toList())) {
        byPath.put(dir.toPath().relativize(p).toString(), Files.readString(p));
      }
    }
    return byPath;
  }

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