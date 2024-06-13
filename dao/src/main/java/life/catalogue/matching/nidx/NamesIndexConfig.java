package life.catalogue.matching.nidx;

import java.io.File;

import javax.annotation.Nullable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class NamesIndexConfig {

  public enum Store {MAPDB, CHRONICLE}

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

  @NotNull
  public Store type = Store.MAPDB;

  /**
   * Maximum numbers of names index entries supported by a chronicle store
   */
  @Min(1_000)
  public int maxEntries = 50_000_000;

}
