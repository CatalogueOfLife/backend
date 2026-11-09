package life.catalogue.config;

import java.io.File;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NormalizerConfigTest {

  @Test
  public void attemptFromArchive() {
    assertEquals(2, NormalizerConfig.attemptFromArchive(new File("0002.archive")));
    assertEquals(2, NormalizerConfig.attemptFromArchive(new File("2.archive")));
    assertEquals(1002, NormalizerConfig.attemptFromArchive(new File("001002.archive")));
  }

  /**
   * File names from clients, e.g. the X-File-Name header of an upload, must stay inside the dataset scratch dir.
   * See gbif/informatics#85 (B1).
   */
  @Test
  public void scratchFile() {
    NormalizerConfig cfg = new NormalizerConfig();
    cfg.scratchDir = new File("/tmp/col-scratch-test");
    var dir = cfg.scratchDir(3).toPath().toAbsolutePath().normalize();

    assertEquals(dir.resolve("data.zip").toFile(), cfg.scratchFile(3, "data.zip"));
    assertEquals(dir.resolve("upload-123.csv").toFile(), cfg.scratchFile(3, "upload-123.csv"));
    assertEquals(dir.resolve("..data.zip").toFile(), cfg.scratchFile(3, "..data.zip"));

    for (String fn : new String[]{null, "", " ", ".", "..", "../x", "a/../../x", "/etc/passwd", "..\\x", "a\\b",
      "a\u0000b", "a\nb", "normalizer", "Source"}) {
      try {
        cfg.scratchFile(3, fn);
        fail("File name accepted: " + fn);
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
  }
}
