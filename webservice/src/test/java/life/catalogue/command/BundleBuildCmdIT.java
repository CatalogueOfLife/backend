package life.catalogue.command;

import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Builds a complete bundle data artifact for the small apple test dataset.
 * Needs the postgres client tools on the PATH for the final pg_dump and is skipped without them.
 */
public class BundleBuildCmdIT extends CmdTestBase {

  public BundleBuildCmdIT() {
    super(BundleBuildCmd::new, TestDataRule.apple());
  }

  @Test
  public void testBuild() throws Exception {
    Assume.assumeTrue("pg_dump is required to build a bundle", pgDumpAvailable());

    File dir = Files.createTempDirectory("col-bundle").toFile();
    dir.delete(); // the command creates it
    try {
      final int key = TestDataRule.APPLE.key;
      assertTrue(run("bundleBuild", "--delete", "--key", Integer.toString(key), "--dir", dir.getAbsolutePath()).isEmpty());

      assertFileWithContent(new File(dir, "release.dump"));
      assertFileWithContent(new File(dir, "bundle.json"));
      File store = new File(dir, "matcher/" + key);
      assertFileWithContent(new File(store, "usages.bin"));
      assertFileWithContent(new File(store, "canonical.bin"));
      assertFileWithContent(new File(store, "dataset.json"));
      assertTrue("names index store missing", new File(dir, "nidx").exists());
      assertTrue("intermediate copy files must be cleaned up", !new File(dir, "pgdumps").exists());

    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(dir);
    }
  }

  private static void assertFileWithContent(File f) {
    assertTrue(f + " does not exist", f.exists());
    assertTrue(f + " is empty", f.length() > 0);
  }

  private static boolean pgDumpAvailable() {
    try {
      return new ProcessBuilder("pg_dump", "--version").start().waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }
}
