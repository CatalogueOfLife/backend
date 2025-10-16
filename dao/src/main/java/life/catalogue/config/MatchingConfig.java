package life.catalogue.config;

import java.io.File;

public class MatchingConfig {

  public boolean chronicle = true;

  /**
   * Directory to store matching storage files, one for each dataset.
   * If null all matching storage is kept in memory only.
   */
  public File storageDir;

  /**
   * Directory with matching data for a specific dataset
   * @param datasetKey
   * @return
   */
  public File dir(int datasetKey) {
    return new File(storageDir, datasetKey+"");
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return storageDir != null && storageDir.mkdirs();
  }

}
