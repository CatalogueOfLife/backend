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
   * Days an on demand matcher — one that exists only because a user matched against a dataset the
   * published invariant does not cover, e.g. a private dataset — is kept after it was last used.
   * 0 or less disables the expiry and keeps such matchers indefinitely.
   */
  public int onDemandTtlDays = 30;

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

  /**
   * Name of the metadata sidecar inside a matcher store directory. It holds the dataset the store was
   * built from - the attempt marker staleness is detected by - and its file modification time doubles as
   * the last used marker for on demand matchers.
   */
  public static final String DATASET_JSON = "dataset.json";

  /**
   * The metadata sidecar of a dataset's matcher store. It lives inside the store directory so a build can
   * write it before the directory is moved into place, making the swap a single atomic rename, and so the
   * store directory is a self contained artifact that can be shipped to a matching server as is.
   */
  public File datasetJson(int datasetKey) {
    return datasetJson(dir(datasetKey));
  }

  public static File datasetJson(File storeDir) {
    return new File(storeDir, DATASET_JSON);
  }

  /** Location the sidecar used to live at, next to the store directory. Only used to migrate it once. */
  public File legacyDatasetJson(int datasetKey) {
    return new File(storageDir, datasetKey + ".json");
  }

  /**
   * Identifies this JVM in the transient build directory names. A build token alone is only unique within a
   * process - two JVMs sharing a storageDir both start their counter at zero - so the pid goes into the path
   * as well and two processes can never write into the same temp dir. Coordinating the builds themselves is
   * a different matter, see {@code UsageMatcherFactory}.
   */
  private static final long JVM_ID = ProcessHandle.current().pid();

  /**
   * Per-build temporary directory a matcher is (re)built into before it is atomically moved to {@link #dir(int)}.
   * Includes a build token unique to this JVM and the pid, so two concurrent builds never share a temp dir.
   * Kept in the same storageDir so the final move is a cheap same-filesystem rename.
   */
  public File buildDir(int datasetKey, long buildToken) {
    return new File(storageDir, datasetKey + "." + JVM_ID + "-" + buildToken + ".building");
  }

  /** Backup dir the previous store is renamed to during a swap, so a failed move can be rolled back. */
  public File backupDir(int datasetKey, long buildToken) {
    return new File(storageDir, datasetKey + "." + JVM_ID + "-" + buildToken + ".old");
  }

  /** True for the transient {@code .building}/{@code .old} dirs created during a swap (not a real matcher store). */
  public static boolean isTransientDir(String name) {
    return name.endsWith(".building") || name.endsWith(".old");
  }

  /**
   * The pid {@link #buildDir(int, long)} put into a transient dir name, so a sweep can tell a crash leftover
   * from the live build of another process sharing this storageDir.
   * @return null if the name is not a transient dir or predates the pid in the name
   */
  public static Long transientDirPid(String name) {
    if (!isTransientDir(name)) {
      return null;
    }
    int firstDot = name.indexOf('.');
    int dash = firstDot < 0 ? -1 : name.indexOf('-', firstDot);
    if (dash < 0) {
      return null;
    }
    try {
      return Long.parseLong(name.substring(firstDot + 1, dash));
    } catch (NumberFormatException e) {
      return null;
    }
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
