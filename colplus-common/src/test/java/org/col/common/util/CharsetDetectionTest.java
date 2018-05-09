package org.col.common.util;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.col.common.io.CharsetDetection;
import org.col.common.io.PathUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class CharsetDetectionTest {

  @Test
  public void detectEncoding() throws Exception {
    for (Path p : testFiles()) {
      System.out.println("\n***** " + PathUtils.getFilename(p) + " *****");
      Charset expected = expectedCharset(p);
      Charset detected = CharsetDetection.detectEncoding(p);
      System.out.println(" -> " + detected);
      assertEquals(PathUtils.getFilename(p), expected, detected);
    }
  }

  public static Iterable<Path> testFiles() throws IOException, URISyntaxException {
    Path folder = PathUtils.classPathTestRes("charsets");

    return Files.newDirectoryStream(folder, new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        return Files.isRegularFile(p);
      }
    });
  }

  public static Charset expectedCharset(Path p) throws URISyntaxException {
    String name = PathUtils.getBasename(p);
    int idx = name.indexOf("_");
    if (idx > 0) {
      name = name.substring(0, idx);
    }
    return Charset.forName(name);
  }

  private static void showBits(byte param) {
    int mask = 1 << 8;

    for (int i = 1; i <= 8; i++,
        param <<= 1) {
      System.out.print((param & mask) ==
          0 ? "0" : "1");
      if (i % 8 == 0)
        System.out.print(" ");
    }
    System.out.println();
  }
}