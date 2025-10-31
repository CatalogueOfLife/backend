package life.catalogue.importer.neo;

import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.NormalizerConfig;

import java.io.File;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.mapdb.DBMaker;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for persistent & temporary, volatile neodb instances.
 * The factory was originally designed to run a dedicated, single DatabaseManagementService,
 * but the community edition of neo4j does not allow to create multiple databases.
 *
 * So now we create a new embedded service for each neodb instance.
 */
public class NeoDbFactory {
  private static final Logger LOG = LoggerFactory.getLogger(NeoDbFactory.class);

  private final NormalizerConfig cfg;
  private final Path dir;


  public NeoDbFactory(NormalizerConfig cfg) {
    this.cfg = cfg;
    this.dir = cfg.neoDir().toPath();
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
    final File storeDir = dbDir(datasetKey); // used for both neo & mapdb
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

      // neo4j embedded
      DatabaseManagementService service = newEmbeddedService(storeDir.toPath());
      return new NeoDb(datasetKey, attempt, dbMaker.make(), storeDir, service, cfg.batchSize, cfg.batchTimeout);

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
  private DatabaseManagementService newEmbeddedService(Path storeDir) {
    var dbService = new DatabaseManagementServiceBuilder( storeDir )
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

}

