package life.catalogue.command;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPOutputStream;

import org.apache.ibatis.session.SqlSession;

import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
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
      // apple is EXTERNAL, so it has no mother project - its metrics live under its own key and attempt
      final int attempt = createImportWithMetrics(key);

      assertTrue(run("bundleBuild", "--delete", "--key", Integer.toString(key), "--dir", dir.getAbsolutePath()).isEmpty());

      assertFileWithContent(new File(dir, "release.dump"));
      assertFileWithContent(new File(dir, "bundle.json"));
      File store = new File(dir, "matcher/" + key);
      assertFileWithContent(new File(store, "usages.bin"));
      assertFileWithContent(new File(store, "canonical.bin"));
      assertFileWithContent(new File(store, "dataset.json"));
      assertTrue("names index store missing", new File(dir, "nidx").exists());
      assertTrue("intermediate copy files must be cleaned up", !new File(dir, "pgdumps").exists());

      // a dataset without a mother project must still ship its file metrics
      var shipped = new FileMetricsDatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), new File(dir, "metrics"));
      assertFileWithContent(shipped.namesFile(key, attempt));

      // the artifact must be runnable as downloaded: compose file, app config, restore hook and readme
      File compose = new File(dir, "docker-compose.yml");
      File config = new File(dir, "config.yml");
      File restore = new File(dir, "restore.sh");
      assertFileWithContent(compose);
      assertFileWithContent(config);
      assertFileWithContent(restore);
      assertFileWithContent(new File(dir, "README.md"));
      assertTrue("restore hook must be executable", restore.canExecute());
      // no placeholder may survive, and the release key must be baked in
      for (File f : new File[]{compose, config, restore, new File(dir, "README.md")}) {
        String content = Files.readString(f.toPath());
        assertFalse(f + " still has placeholders", content.contains("{{"));
      }
      assertTrue("release key not baked into the config",
        Files.readString(config.toPath()).contains("releaseKey: " + key));
      assertTrue("image not baked into the compose file",
        Files.readString(compose.toPath()).contains(BundleBuildCmd.DEFAULT_IMAGE));

    } finally {
      org.apache.commons.io.FileUtils.deleteQuietly(dir);
    }
  }

  /**
   * Gives the test dataset an import and a names metrics file, the way a real dataset has after being
   * imported. Without one there is nothing for the bundle to copy and the metrics assertion is vacuous.
   */
  private int createImportWithMetrics(int key) throws Exception {
    final int attempt;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      DatasetImport di = new DatasetImport();
      di.setDatasetKey(key);
      di.setOrigin(DatasetOrigin.EXTERNAL);
      di.setCreatedBy(TestDataRule.TEST_USER.getKey());
      di.setNameCount(2);
      session.getMapper(DatasetImportMapper.class).create(di);
      attempt = di.getAttempt();
    }
    var dao = new FileMetricsDatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), metricsRepo());
    File f = dao.namesFile(key, attempt);
    f.getParentFile().mkdirs();
    try (OutputStream out = new GZIPOutputStream(new java.io.FileOutputStream(f))) {
      out.write("Larus fuscus\nLarus argentatus\n".getBytes(StandardCharsets.UTF_8));
    }
    return attempt;
  }

  /**
   * The very metricsRepo the command will read, taken from the config file CmdTestBase wrote, so the
   * test cannot drift from the maven filtered path in config-test.yaml.
   */
  private File metricsRepo() throws Exception {
    for (String line : Files.readAllLines(cfg.file.toPath())) {
      if (line.startsWith("metricsRepo:")) {
        return new File(line.substring("metricsRepo:".length()).trim());
      }
    }
    throw new IllegalStateException("no metricsRepo in " + cfg.file);
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
