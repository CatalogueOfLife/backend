package life.catalogue.importer.neo;

import life.catalogue.common.Managed;
import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.NormalizerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.mapdb.DBMaker;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseExistsException;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.dbms.api.DatabaseNotFoundException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.neo4j.configuration.GraphDatabaseSettings.DEFAULT_DATABASE_NAME;

/**
 * A factory for persistent & temporary, volatile neodb instances.
 * The factory runs a dedicated DatabaseManagementService and should be a singleton.
 */
public class NeoDbFactory implements Managed {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDbFactory.class);

  private final NormalizerConfig cfg;
  private final Path dir;
  private DatabaseManagementService service;


  public NeoDbFactory(NormalizerConfig cfg) {
    this.cfg = cfg;
    this.dir = cfg.neoDir().toPath();
  }

  private String dbName(int datasetKey) {
    return "db-"+datasetKey;
  }

  private File dbDir(int datasetKey) {
    return dir.resolve(String.valueOf(datasetKey)).toFile();
  }
  
  /**
   * A backend that is stored in files inside the configured normalizer directory.
   *
   * @return creates a new, empty, persistent dao wiping any data that might have existed for that dataset
   */
  public NeoDb create(int datasetKey, int attempt) {
    final File storeDir = dbDir(datasetKey); // only used for mapdb, not neo!
    try {
      LOG.info("Create new neodb {} with storage at {}", datasetKey, storeDir);

      if (storeDir.exists()) {
        FileUtils.deleteQuietly(storeDir);
      }
      // make sure mapdb parent dirs exist
      if (!storeDir.exists()) {
        storeDir.mkdirs();
      }

      final File mapDbFile = mapDbFile(storeDir);
      DBMaker.Maker dbMaker = DBMaker
        .fileDB(mapDbFile)
        .fileMmapEnableIfSupported();

      GraphDatabaseService graphDb = service.database( DEFAULT_DATABASE_NAME );
      return new NeoDb(datasetKey, attempt, dbMaker.make(), storeDir, graphDb, cfg.batchSize, cfg.batchTimeout);

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
   * Creates a new embedded db service in the configured directory folder.
   */
  private DatabaseManagementService newEmbeddedService() {
    var dbService = new DatabaseManagementServiceBuilder( dir )
      .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
      .setConfig(GraphDatabaseSettings.pagecache_memory, (long) cfg.mappedMemory * 1024 * 1024)
      .build();

    // Registers a shutdown hook for the Neo4j instance so that it
    // shuts down nicely when the VM exits.
    Runtime.getRuntime().addShutdownHook( new Thread() {
      @Override
      public void run()
      {
        dbService.shutdown();
      }
    } );
    return dbService;
  }

  private static File mapDbFile(File neoDir) {
    return new File(neoDir, "mapdb.bin");
  }

  @Override
  public void start() throws Exception {
    if (service == null) {
      LOG.info("Starting neodb factory service in {}", dir);
      service = newEmbeddedService();
    }
  }

  @Override
  public void stop() throws Exception {
    if (service != null) {
      LOG.info("Stopping neodb factory service in {}", dir);
      service.shutdown();
      service = null;
    }
  }

  @Override
  public boolean hasStarted() {
    return service != null;
  }
}

