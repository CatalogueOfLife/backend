package life.catalogue.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 *
 */
@SuppressWarnings("PublicField")
public class NormalizerConfig {
  
  private static final Logger LOG = LoggerFactory.getLogger(NormalizerConfig.class);
  public static final String ARCHIVE_SUFFIX = "archive";
  private static final Pattern ARCHIVE_FN_PATTERN = Pattern.compile("^0*(\\d+)\\." + ARCHIVE_SUFFIX + "$");

  /**
   * Archive directory to store larger amount of data
   */
  @NotNull
  public File archiveDir = new File("/tmp");
  
  /**
   * Temporary folder for fast IO.
   * Used primarily for neo4j dbs.
   */
  @NotNull
  public File scratchDir = new File("/tmp/col");
  
  /**
   * Batchsize to use when processing all nodes, e.g. for normalising the flat classification
   */
  @NotNull
  public int batchSize = 10000;
  
  /**
   * Timeout in minutes to wait before stopping processing a batch in neodb and fail the normalizer / import
   */
  @NotNull
  public int batchTimeout = 30;
  
  @Min(0)
  public int mappedMemory = 128;
  
  /**
   * The dataset source files as a single archive in original format (zip, gzip, etc).
   * Stored in special archive directory so we can keep large amounts of data on cheap storage devices
   * and compare files for changes.
   */
  public File archive(int datasetKey, int attempt) {
    return new File(archiveDir(datasetKey), String.format("%04d.%s", attempt, ARCHIVE_SUFFIX));
  }

  public File archiveDir(int datasetKey) {
    return new File(archiveDir, String.valueOf(datasetKey));
  }

  /**
   * Returns the symlink to latest archive that has been used for imports and is archived and symlinked.
   * The method does not query the database for imports and the import returned might not be the last successful one.
   * If no archive exists null is returned.
   */
  public File lastestArchive(int datasetKey) throws IOException {
    Path latest = lastestArchiveSymlink(datasetKey).toPath();
    if (Files.exists(latest)) {
      return latest.toRealPath().toFile();
    }
    return null;
  }

  /**
   * Return the symlink that links to the latest stored archive attempt
   * @param datasetKey
   * @return
   */
  public File lastestArchiveSymlink(int datasetKey) {
    return new File(archiveDir, String.format("%d/latest.%s", datasetKey, ARCHIVE_SUFFIX));
  }

  public static int attemptFromArchive(File archiveFile) {
    var m = ARCHIVE_FN_PATTERN.matcher(archiveFile.getName());
    if (m.find()) {
      return Integer.parseInt(m.group(1));
    }
    throw new IllegalArgumentException("Filename not an attempt based archive: " + archiveFile.getName());
  }

  public File scratchDir(String subdir) {
    return new File(scratchDir, subdir);
  }

  public File scratchDir(int datasetKey) {
    return new File(scratchDir, String.valueOf(datasetKey));
  }
  
  public File neoDir(int datasetKey) {
    return new File(scratchDir(datasetKey), "normalizer");
  }
  
  /**
   * Directory with all decompressed source files
   */
  public File sourceDir(int datasetKey) {
    return new File(scratchDir(datasetKey), "source");
  }
  
  public File scratchFile(int datasetKey, String fileName) {
    Preconditions.checkArgument(!fileName.equalsIgnoreCase("normalizer") && !fileName.equalsIgnoreCase("source"));
    return new File(scratchDir(datasetKey), fileName);
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    boolean created = archiveDir.mkdirs();
    return scratchDir.mkdirs() || created;
  }
}


