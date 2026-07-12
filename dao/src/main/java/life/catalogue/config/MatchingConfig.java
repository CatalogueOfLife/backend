package life.catalogue.config;

import jakarta.validation.constraints.NotNull;

import java.io.File;
import java.util.UUID;

public class MatchingConfig {

  /**
   * Directory to store matching storage files, one for each dataset.
   * If null all matching storage is kept in memory only.
   */
  public File storageDir;

  /**
   * Datasets with fewer usages than this value use a Postgres-backed matcher instead of a
   * persistent file store. 0 disables the threshold (all datasets use persistent matchers).
   */
  public int pgMatcherThreshold = 100;

  /**
   * Temporary folder for file uploads.
   */
  @NotNull
  public File uploadDir = new File("/tmp/col/upload");

  /**
   * Directory with matching data for a specific dataset
   * @param datasetKey
   * @return
   */
  public File dir(int datasetKey) {
    return new File(storageDir, datasetKey + "");
  }

  public File datasetJson(int datasetKey) {
    return new File(storageDir, datasetKey + ".json");
  }

  /**
   * Per-build temporary directory a matcher is (re)built into before it is atomically moved to {@link #dir(int)}.
   * Includes a unique build token so two concurrent builds of the same dataset never share a temp dir.
   * Kept in the same storageDir so the final move is a cheap same-filesystem rename.
   */
  public File buildDir(int datasetKey, long buildToken) {
    return new File(storageDir, datasetKey + "." + buildToken + ".building");
  }

  /** Backup dir the previous store is renamed to during a swap, so a failed move can be rolled back. */
  public File backupDir(int datasetKey, long buildToken) {
    return new File(storageDir, datasetKey + "." + buildToken + ".old");
  }

  /** True for the transient {@code .building}/{@code .old} dirs created during a swap (not a real matcher store). */
  public static boolean isTransientDir(String name) {
    return name.endsWith(".building") || name.endsWith(".old");
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return uploadDir.mkdirs() || storageDir != null && storageDir.mkdirs();
  }

  /**
   * Creates a new random & unique scratch file that can e.g. be used for uploads.
   */
  public File randomUploadFile(String prefix, String suffix) {
    return new File(uploadDir, prefix + UUID.randomUUID() + suffix);
  }

  public File randomUploadFile(String suffix) {
    return randomUploadFile("", suffix);
  }
}
