package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.common.lang.InterruptedRuntimeException;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.csv.ColdpReader;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.importer.neo.NodeBatchProcessor;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    NeoDb db = NeoDbFactory.create(datasetKey, 1, cfg);

    URL url = getClass().getResource("/coldp/20");
    Path src = Paths.get(url.toURI());

    //Path src = cfg.sourceDir(datasetKey).toPath();
    java.nio.file.Files.createDirectories(src);
    NeoCsvInserter ins = new NeoCsvInserter(src, ColdpReader.from(src), db, new DatasetSettings(), null) {
      @Override
      protected void batchInsert() throws NormalizationFailedException, InterruptedException, InterruptedRuntimeException {
        throw new InterruptedRuntimeException("no inserts");
      }

      @Override
      protected NodeBatchProcessor relationProcessor() {
        return null;
      }
    };

    // InterruptedException
    try {
      ins.insertAll();
      fail("Expected InterruptedException");
    } catch (InterruptedException e) {
      // good!
    } finally {
      db.closeAndDelete();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
}