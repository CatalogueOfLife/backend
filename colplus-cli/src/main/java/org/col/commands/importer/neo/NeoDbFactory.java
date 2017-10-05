package org.col.commands.importer.neo;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.commands.importer.neo.model.TaxonNameNode;
import org.col.config.NeoConfig;
import org.col.util.CleanupUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * A factory for persistent & temporary, volatile neodb instances.
 */
public class NeoDbFactory {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDbFactory.class);

  /**
   * A memory based backend which is erased after the JVM exits.
   * Useful for short lived tests. Neo4j always persists some files which are cleaned up afterwards automatically
   *
   * @param mappedMemory used for the neo4j db
   */
  public static NeoDb<TaxonNameNode> temporaryDb(int mappedMemory) {
    LOG.debug("Create new in memory dao");
    DB kvp = DBMaker.memoryDB()
        .make();

    File storeDir = Files.createTempDir();
    NeoConfig cfg = new NeoConfig();
    cfg.mappedMemory = mappedMemory;
    GraphDatabaseBuilder builder = cfg.newEmbeddedDb(storeDir, false, null);
    CleanupUtils.registerCleanupHook(storeDir);

    return new NeoDb<TaxonNameNode>(TaxonNameNode.class, kvp, storeDir, null, builder);
  }

  /**
   * A backend that is stored in files inside the configured neo directory.
   *
   * @param eraseExisting if true erases any previous data files
   */
  private static NeoDb<TaxonNameNode> persistentDb(NeoConfig cfg, int datasetKey, boolean eraseExisting) {
    DB kvp = null;
    try {
      final File kvpF = cfg.kvp(datasetKey);
      final File storeDir = cfg.neoDir(datasetKey);
      if (eraseExisting) {
        LOG.debug("Remove existing data store");
        if (kvpF.exists()) {
          kvpF.delete();
        }
      }
      FileUtils.forceMkdir(kvpF.getParentFile());
      LOG.debug("Use KVP store {}", kvpF.getAbsolutePath());
      kvp = DBMaker.fileDB(kvpF)
          .fileMmapEnableIfSupported()
          .make();
      GraphDatabaseBuilder builder = cfg.newEmbeddedDb(storeDir, eraseExisting, null);
      return new NeoDb<TaxonNameNode>(TaxonNameNode.class, kvp, storeDir, kvpF, builder);

    } catch (Exception e) {
      if (kvp != null && !kvp.isClosed()) {
        kvp.close();
      }
      throw new IllegalStateException("Failed to init persistent DAO for " + datasetKey, e);
    }
  }

  /**
   * @return the neodb for an existing, persistent db
   */
  public static NeoDb<TaxonNameNode> open(NeoConfig cfg, int datasetKey) {
    return persistentDb(cfg, datasetKey, false);
  }

  /**
   * @return creates a new, empty, persistent dao wiping any data that might have existed for that dataset
   */
  public static NeoDb<TaxonNameNode> create(NeoConfig cfg, int datasetKey) {
    return persistentDb(cfg, datasetKey, true);
  }

}

