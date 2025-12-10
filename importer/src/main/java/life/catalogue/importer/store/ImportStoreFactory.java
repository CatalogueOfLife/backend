package life.catalogue.importer.store;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

import life.catalogue.common.lang.Exceptions;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.NormalizerConfig;

import java.io.File;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.mapdb.DBMaker;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A factory for persistent import storage instances.
 */
public class ImportStoreFactory {
  private static final Logger LOG = LoggerFactory.getLogger(ImportStoreFactory.class);

  private final NormalizerConfig cfg;
  private final Path dir; // parent dir for all storage instances
  private final Pool<Kryo> pool;

  public ImportStoreFactory(NormalizerConfig cfg) {
    this.cfg = cfg;
    this.dir = cfg.importStorageDir().toPath();
    pool = new ImportKryoPool(cfg.kryoPoolSize);
  }

  private File dbDir(int datasetKey) {
    return dir.resolve(String.valueOf(datasetKey)).toFile();
  }
  
  /**
   * A backend that is stored in files inside the configured normalizer directory.
   *
   * @return creates a new, empty, persistent dao wiping any data that might have existed for that dataset
   */
  public ImportStore create(int datasetKey, int attempt) {
    final File storeDir = dbDir(datasetKey); // used for both neo & mapdb
    LOG.info("Create new import storage for dataset {} at {}", datasetKey, storeDir);

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

    return new ImportStore(datasetKey, attempt, dbMaker.make(), storeDir, pool);
  }

  private static File mapDbFile(File neoDir) {
    return new File(neoDir, "mapdb.bin");
  }

}

