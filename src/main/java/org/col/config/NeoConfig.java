package org.col.config;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.factory.GraphDatabaseBuilder;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.shell.ShellSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.File;

/**
 *
 */
@SuppressWarnings("PublicField")
public class NeoConfig {

  private static final Logger LOG = LoggerFactory.getLogger(NeoConfig.class);

  @NotNull
  public File neoRepository;

  @NotNull
  public int batchSize = 10000;

  @Min(0)
  public int mappedMemory = 128;

  public File neoDir(int datasetKey) {
    return new File(neoRepository, String.valueOf(datasetKey));
  }

  /**
   * @return the KVP dbmap file used for the given dataset
   */
  public File kvp(int datasetKey) {
    return new File(neoRepository, String.valueOf(datasetKey)+".kvp");
  }

  /**
   * Creates a new embedded db in the neoRepository folder.
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
        .newEmbeddedDatabaseBuilder(storeDir)
        .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
        .setConfig(GraphDatabaseSettings.allow_store_upgrade, "true")
        .setConfig(GraphDatabaseSettings.pagecache_memory, mappedMemory + "M");
    if (shellPort != null) {
      LOG.info("Enable neo4j shell on port " + shellPort);
      builder.setConfig(ShellSettings.remote_shell_enabled, "true")
          .setConfig(ShellSettings.remote_shell_port, shellPort.toString())
          // listen to all IPs, not localhost only
          .setConfig(ShellSettings.remote_shell_host, "0.0.0.0");
    }
    return builder;
  }

}


