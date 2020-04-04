package life.catalogue.importer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.io.Files;
import life.catalogue.common.io.Resources;
import org.apache.commons.io.FileUtils;
import life.catalogue.api.model.Dataset;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import org.junit.After;
import org.junit.Before;

public abstract class InserterBaseTest {
  protected Dataset d;
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
      d = new Dataset();
      d.setKey(1);
      store.put(d);

      URL url = getClass().getResource(resource);
      Path path = Paths.get(url.toURI());
  
      return newInserter(path);
      
    } catch (IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  
  public abstract NeoInserter newInserter(Path resource) throws IOException;
  
}
