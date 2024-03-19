package life.catalogue.importer.neo;

import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.NormalizerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;

import org.apache.commons.io.FileUtils;
import org.mapdb.DBMaker;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.logging.slf4j.Slf4jLogProvider;
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
  private static NeoDb persistentDb(NormalizerConfig cfg, int datasetKey, int attempt, boolean eraseExisting) throws IOException {
    final File storeDir = cfg.neoDir(datasetKey);
    LOG.info("{} persistent NormalizerStore at {}", eraseExisting ? "Create" : "Open", storeDir);
    final File mapDbFile = mapDbFile(storeDir);
    DBMaker.Maker dbMaker = DBMaker
        .fileDB(mapDbFile)
        .fileMmapEnableIfSupported();
    return create(datasetKey, attempt, cfg, storeDir, eraseExisting, dbMaker);
  }
  
  /**
   * A backend that is stored in files inside the configured normalizer directory.
   *
   * @param eraseExisting if true erases any previous data files
   */
  private static NeoDb create(int datasetKey, int attempt, NormalizerConfig cfg, File storeDir, boolean eraseExisting, DBMaker.Maker dbMaker) {
    try {
      GraphDatabaseBuilder builder = newEmbeddedDb(cfg, storeDir, eraseExisting);
      
      // make sure mapdb parent dirs exist
      if (!storeDir.exists()) {
        storeDir.mkdirs();
      }
      return new NeoDb(datasetKey, attempt, dbMaker.make(), storeDir, builder, cfg.batchSize, cfg.batchTimeout);

    } catch (RuntimeException e) {
      // can be caused by interruption in mapdb
      Throwable root = Exceptions.getRootCause(e);
      if (root instanceof ClosedByInterruptException || root instanceof InterruptedException) {
        throw new InterruptedRuntimeException("Failed to create NeoDB, thread was interrupted", e);
      }
      throw new IllegalStateException(String.format("Failed to init NormalizerStore at %s. Cause: %s", storeDir, root), e);
    }
  }


  /**
   * Creates a new embedded db in the directory folder.
   *
   * @param eraseExisting if true deletes previously existing db
   */
  private static GraphDatabaseBuilder newEmbeddedDb(NormalizerConfig cfg, File storeDir, boolean eraseExisting) {
    if (eraseExisting && storeDir.exists()) {
      // erase previous db
      LOG.debug("Removing previous neo4j database from {}", storeDir.getAbsolutePath());
      if (!FileUtils.deleteQuietly(storeDir)) {
        LOG.warn("Unable to remove previous neo4j database from {}", storeDir.getAbsolutePath());
      }
    }

    var managementService = new DatabaseManagementServiceBuilder( storeDir.toPath() ).build();
    var graphDb = managementService.database( DEFAULT_DATABASE_NAME );
    registerShutdownHook( managementService );

    GraphDatabaseBuilder builder = new GraphDatabaseFactory()
      .setUserLogProvider(new Slf4jLogProvider())
      .newEmbeddedDatabaseBuilder(storeDir)
      .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
      .setConfig(GraphDatabaseSettings.allow_upgrade, "true")
      .setConfig(GraphDatabaseSettings.pagecache_memory, cfg.mappedMemory + "M");
    return builder;
  }

  /**
   * @return the neodb for an existing, persistent db
   */
  public static NeoDb open(int datasetKey, int attempt, NormalizerConfig cfg) throws IOException {
    return persistentDb(cfg, datasetKey, attempt, false);
  }
  
  /**
   * @return creates a new, empty, persistent dao wiping any data that might have existed for that dataset
   */
  public static NeoDb create(int datasetKey, int attempt, NormalizerConfig cfg) throws IOException {
    return persistentDb(cfg, datasetKey, attempt, true);
  }
  
  private static File mapDbFile(File neoDir) {
    return new File(neoDir, "mapdb.bin");
  }
}

