package life.catalogue.importer;

import com.google.common.io.Files;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class InserterBaseTest {
  protected DatasetWithSettings d;
  protected NeoDb store;
  protected NormalizerConfig cfg;
  
  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
  }
  
  @After
  public void cleanup() throws Exception {
    if (store != null) {
      store.closeAndDelete();
    }
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }
  
  protected NeoInserter setup(String resource) {
    try {
      store = NeoDbFactory.create(1, 1, cfg);
      d = new DatasetWithSettings();
      d.setKey(1);

      URL url = getClass().getResource(resource);
      Path path = Paths.get(url.toURI());
  
      return newInserter(path, d.getSettings());
      
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  
  public abstract NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException;
  
}
