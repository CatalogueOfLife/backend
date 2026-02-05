package life.catalogue;

import life.catalogue.common.io.CompressionUtil;
import life.catalogue.common.io.Resources;
import life.catalogue.config.NormalizerConfig;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.file.PathUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class ImportSourceRunner {
  static final NormalizerConfig cfg = new NormalizerConfig();
  static {
    cfg.scratchDir = new File("/tmp/test/clb");
  }
  static File archive;
  static boolean extraDelete = false;

  static void doImport(int datasetKey) throws IOException {
    final Path sourceDir = cfg.sourceDir(datasetKey).toPath();
    try {
      System.out.println("\nSOURCE " + sourceDir);
      System.out.println("Extracting files from archive");
      CompressionUtil.decompressFile(sourceDir.toFile(), archive);
    } finally {
      // remove source scratch folder with import store and decompressed dwca folders
      final File scratchDir = cfg.scratchDir(datasetKey);
      System.out.println("Remove scratch dir " + scratchDir.getAbsolutePath());
      try {
        if (extraDelete) {
          PathUtils.deleteDirectory(sourceDir); // this is nested in the one below, but we see stale open files piling up
        }
        FileUtils.deleteDirectory(scratchDir);
      } catch (IOException e) {
        System.err.println("Failed to remove scratch dir " + scratchDir);
        System.err.println(e.getMessage());
        e.printStackTrace();
      }
    }
  }

  public static void main(String[] args) throws Exception {
    int iterations = Integer.parseInt(args[0]);
    int time = Integer.parseInt(args[1]);
    System.out.println("Run " + iterations + " imports and wait " +time + " seconds at the very end!\n");

    FileUtils.forceMkdir(cfg.scratchDir);
    archive = new File(cfg.scratchDir, "archive.zip");
    System.out.println("Copy coldp.zip from resources to " + archive.getAbsolutePath());
    Resources.copy("import-runner-test/coldp.zip", archive);

    extraDelete = args.length > 2;

    for (int x = 0; x < iterations; x++) {
      doImport(x);
    }

    System.out.println("Waiting for " + time + "s...");
    TimeUnit.SECONDS.sleep(time);
    System.out.println("Good bye");
  }
}
