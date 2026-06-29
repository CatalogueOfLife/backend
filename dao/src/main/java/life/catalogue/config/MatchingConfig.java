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
