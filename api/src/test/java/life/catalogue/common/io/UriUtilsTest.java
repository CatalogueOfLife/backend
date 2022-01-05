package life.catalogue.common.io;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UriUtilsTest {

  @Test
  public void isFile() {
    assertTrue(UriUtils.isFile(new File("/tmp/me.txt").toURI()));
    assertTrue(UriUtils.isFile(new File("me.txt").toURI()));
    assertTrue(UriUtils.isFile(Paths.get("/tmp/me", "doc.txt").toFile().toURI()));

    assertFalse(UriUtils.isFile(URI.create("http://gbif.org/my.xls")));
    assertFalse(UriUtils.isFile(URI.create("ftp://gbif.org/my.xls")));
    assertFalse(UriUtils.isFile(URI.create("www.gbif.org")));
  }
}