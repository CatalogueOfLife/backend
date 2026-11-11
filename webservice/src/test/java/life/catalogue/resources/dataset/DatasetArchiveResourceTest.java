package life.catalogue.resources.dataset;

import life.catalogue.config.NormalizerConfig;

import java.io.File;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

public class DatasetArchiveResourceTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  /**
   * The archive served without an explicit attempt is the one of the last successful import, not the latest stored one:
   * the latest symlink already moves to a new upload before its import ran, so a broken upload would be served.
   */
  @Test
  public void currentArchive() throws Exception {
    NormalizerConfig cfg = new NormalizerConfig();
    cfg.archiveDir = tmp.getRoot();
    final int key = 1001;
    File good = cfg.archive(key, 3);
    File broken = cfg.archive(key, 4);
    good.getParentFile().mkdirs();
    Files.writeString(good.toPath(), "good");
    Files.writeString(broken.toPath(), "broken");
    File latest = cfg.lastestArchiveSymlink(key);
    Files.createSymbolicLink(latest.toPath(), broken.toPath());

    assertEquals(good, DatasetArchiveResource.currentArchive(cfg, key, 3));
    // never imported successfully: the latest archive is all there is
    assertEquals(latest, DatasetArchiveResource.currentArchive(cfg, key, null));
    // the archive of the current attempt is gone
    assertEquals(latest, DatasetArchiveResource.currentArchive(cfg, key, 2));
  }
}
