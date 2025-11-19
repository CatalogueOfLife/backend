package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.csv.ColdpReader;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.ImportStoreFactory;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;

import org.apache.commons.io.FileUtils;
import org.junit.Test;



import com.google.common.io.Files;

import static org.junit.Assert.fail;

public class NeoCsvInserterTest {

  @Test
  public void interruptInsertAll() throws Exception{
    final int datasetKey = 3;
    NormalizerConfig cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    ImportStoreFactory factory = new ImportStoreFactory(cfg);
    ImportStore db = factory.create(datasetKey, 1);

    URL url = getClass().getResource("/coldp/20");
    Path src = Paths.get(url.toURI());

    //Path src = cfg.sourceDir(datasetKey).toPath();
    java.nio.file.Files.createDirectories(src);
    DataCsvInserter ins = new DataCsvInserter(src, ColdpReader.from(src), db, new DatasetSettings(), null) {
      @Override
      protected void insert() throws NormalizationFailedException, InterruptedException, InterruptedRuntimeException {
        throw new InterruptedRuntimeException("no inserts");
      }
    };

    // InterruptedException
    try {
      ins.insertAll();
      fail("Expected InterruptedException");
    } catch (InterruptedException e) {
      // good!
    } finally {
      db.close();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
}