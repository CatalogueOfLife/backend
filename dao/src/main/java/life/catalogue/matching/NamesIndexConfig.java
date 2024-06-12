package life.catalogue.matching;

import javax.annotation.Nullable;

import java.io.File;

public class NamesIndexConfig {

  public static NamesIndexConfig memory(int poolsize){
    var cfg = new NamesIndexConfig();
    cfg.file = null;
    cfg.kryoPoolSize = poolsize;
    return cfg;
  }

  public static NamesIndexConfig file(File location, int poolsize){
    var cfg = new NamesIndexConfig();
    cfg.file = location;
    cfg.verification = true;
    cfg.kryoPoolSize = poolsize;
    return cfg;
  }

  /**
   * Names index kvp file to persist map on disk.
   * If empty will use a passthrough index that always returns no matches
   */
  @Nullable
  public File file;

  /**
   * If true verifies the existing names index file if it is in sync with the latest index in the database.
   * For a large names index reloading it from the database can take an hour.
   */
  public boolean verification = true;

  public int kryoPoolSize = 1024;

}
