package life.catalogue.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import jakarta.validation.constraints.NotNull;

/**
 *
 */
@SuppressWarnings("PublicField")
public class NormalizerConfig {
  // path separators and control characters incl. NUL
  private static final Pattern INVALID_FILENAME = Pattern.compile("[/\\\\\\p{Cntrl}]");

  
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
   * Used primarily for import dbs.
   */
  @NotNull
  public File scratchDir = new File("/tmp/col");

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

  public File scratchDir(UUID jobKey) {
    return new File(scratchDir, "job/" + jobKey.toString());
  }

  public File importStorageDir() {
    return scratchDir("import-store");
  }

  /**
   * Directory with all decompressed source files
   */
  public File sourceDir(int datasetKey) {
    return new File(scratchDir(datasetKey), "source");
  }
  
  /**
   * A file directly inside the scratch dir of a dataset.
   * The name can come from a client, e.g. the X-File-Name header of an upload, and is therefore restricted to a plain
   * file name: no path separators, no relative segments, no control characters. See gbif/informatics#85 (B1).
   *
   * @throws IllegalArgumentException if the name is not a plain file name or a reserved one
   */
  public File scratchFile(int datasetKey, String fileName) {
    Preconditions.checkArgument(fileName != null && !fileName.isBlank(), "File name required");
    Preconditions.checkArgument(!INVALID_FILENAME.matcher(fileName).find()
        && !fileName.equals(".") && !fileName.equals(".."),
      "Invalid file name %s", fileName);
    Preconditions.checkArgument(!fileName.equalsIgnoreCase("normalizer") && !fileName.equalsIgnoreCase("source"),
      "Reserved file name %s", fileName);
    final Path dir = scratchDir(datasetKey).toPath().toAbsolutePath().normalize();
    final Path file = dir.resolve(fileName).normalize();
    Preconditions.checkArgument(file.getParent() != null && file.getParent().equals(dir), "Invalid file name %s", fileName);
    return file.toFile();
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return archiveDir.mkdirs() || scratchDir.mkdirs();
  }
}


