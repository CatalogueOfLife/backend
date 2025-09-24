package life.catalogue.matching;

import jakarta.validation.constraints.NotNull;

import java.io.File;

public class MatchingConfig {

  /**
   * Directory to store matching storage files, one for each dataset.
   * If null all matching storage is kept in memory only.
   */
  public File storageDir;

}
