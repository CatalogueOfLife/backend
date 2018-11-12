package org.col.admin.importer.neo;

import java.io.File;
import java.io.IOException;

import org.col.admin.config.NormalizerConfig;
import org.mapdb.DBMaker;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for persistent & temporary, volatile neodb instances.
 */
public class NeoDbFactory {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDbFactory.class);
  
  /**
   * A backend that is stored in files inside the configured normalizer directory.
   *
   * @param eraseExisting if true erases any previous data files
   */
  private static NeoDb persistentDb(NormalizerConfig cfg, int datasetKey, boolean eraseExisting) throws IOException {
    final File storeDir = cfg.neoDir(datasetKey);
    LOG.debug("{} persistent NormalizerStore at {}", eraseExisting ? "Create" : "Open", storeDir);
    final File mapDbFile = mapDbFile(storeDir);
    DBMaker.Maker dbMaker = DBMaker
        .fileDB(mapDbFile)
        .fileMmapEnableIfSupported();
    return create(datasetKey, cfg, storeDir, eraseExisting, dbMaker);
  }
  
  /**
   * A backend that is stored in files inside the configured normalizer directory.
   *
   * @param eraseExisting if true erases any previous data files
   */
  private static NeoDb create(int datasetKey, NormalizerConfig cfg, File storeDir, boolean eraseExisting, DBMaker.Maker dbMaker) {
    try {
      GraphDatabaseBuilder builder = cfg.newEmbeddedDb(storeDir, eraseExisting, null);
      
      // make sure mapdb parent dirs exist
      if (!storeDir.exists()) {
        storeDir.mkdirs();
      }
      return new NeoDb(datasetKey, dbMaker.make(), storeDir, builder, cfg.batchSize, cfg.batchTimeout);
      
    } catch (Exception e) {
      throw new IllegalStateException("Failed to init NormalizerStore at " + storeDir, e);
    }
  }
  
  /**
   * @return the neodb for an existing, persistent db
   */
  public static NeoDb open(int datasetKey, NormalizerConfig cfg) throws IOException {
    return persistentDb(cfg, datasetKey, false);
  }
  
  /**
   * @return creates a new, empty, persistent dao wiping any data that might have existed for that dataset
   */
  public static NeoDb create(int datasetKey, NormalizerConfig cfg) throws IOException {
    return persistentDb(cfg, datasetKey, true);
  }
  
  private static File mapDbFile(File neoDir) {
    return new File(neoDir, "mapdb.bin");
  }
}

