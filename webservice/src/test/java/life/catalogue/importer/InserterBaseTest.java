package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;

import com.google.common.io.Files;

public abstract class InserterBaseTest {
  protected DatasetWithSettings d;
  protected NeoDb store;
  protected ReferenceFactory refFactory;
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
    URL url = getClass().getResource(resource);
    try {
      return setup(Paths.get(url.toURI()));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  protected NeoInserter setup(Path path) {
    try {
      d = new DatasetWithSettings();
      d.setKey(1);
      store = NeoDbFactory.create(d.getKey(), 1, cfg);
      refFactory = new ReferenceFactory(d.getKey(), store.references(), null);

      return newInserter(path, d.getSettings());
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public abstract NeoInserter newInserter(Path resource, DatasetSettings settings) throws IOException;
  
}
