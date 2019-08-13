package org.col.config;

import java.io.File;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.logging.slf4j.Slf4jLogProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
@SuppressWarnings("PublicField")
public class NormalizerConfig {
  
  private static final Logger LOG = LoggerFactory.getLogger(NormalizerConfig.class);
  
  /**
   * Archive directory to store larger amount of data
   */
  @NotNull
  public File archiveDir = new File("/tmp");
  
  /**
   * Temporary folder for fast IO.
   * Used primarily for neo4j dbs.
   */
  @NotNull
  public File scratchDir = new File("/tmp/colplus");
  
  /**
   * Batchsize to use when processing all nodes, e.g. for normalising the flat classification
   */
  @NotNull
  public int batchSize = 10000;
  
  /**
   * Timeout in minutes to wait before stopping processing a batch in neodb and fail the normalizer / import
   */
  @NotNull
  public int batchTimeout = 30;
  
  @Min(0)
  public int mappedMemory = 128;
  
  /**
   * The dataset source files as a single archive in original format (zip, gzip, etc).
   * Stored in special archive directory so we can keep large amounts of data on cheap storage devices
   * and compare files for changes.
   */
  public File source(int datasetKey) {
    return new File(archiveDir, String.valueOf(datasetKey) + ".archive");
  }
  
  
  public File scratchDir(int datasetKey) {
    return new File(scratchDir, String.valueOf(datasetKey));
  }
  
  public File neoDir(int datasetKey) {
    return new File(scratchDir(datasetKey), "normalizer");
  }
  
  /**
   * Directory with all decompressed source files
   */
  public File sourceDir(int datasetKey) {
    return new File(scratchDir(datasetKey), "source");
  }
  
  public File scratchFile(int datasetKey, String fileName) {
    Preconditions.checkArgument(!fileName.equalsIgnoreCase("normalizer") && !fileName.equalsIgnoreCase("source"));
    return new File(scratchDir(datasetKey), fileName);
  }
  
  /**
   * Creates a new embedded db in the directory folder.
   *
   * @param eraseExisting if true deletes previously existing db
   */
  public GraphDatabaseBuilder newEmbeddedDb(File storeDir, boolean eraseExisting, Integer shellPort) {
    if (eraseExisting && storeDir.exists()) {
      // erase previous db
      LOG.debug("Removing previous neo4j database from {}", storeDir.getAbsolutePath());
      FileUtils.deleteQuietly(storeDir);
    }
    GraphDatabaseBuilder builder = new GraphDatabaseFactory()
        .setUserLogProvider(new Slf4jLogProvider())
        .newEmbeddedDatabaseBuilder(storeDir)
        .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
        .setConfig(GraphDatabaseSettings.allow_upgrade, "true")
        .setConfig(GraphDatabaseSettings.pagecache_memory, mappedMemory + "M");
    return builder;
  }
  
}


