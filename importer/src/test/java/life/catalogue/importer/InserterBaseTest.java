package life.catalogue.importer;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.ImportStoreFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;

import com.google.common.io.Files;

import org.junit.BeforeClass;

public abstract class InserterBaseTest {
  protected DatasetWithSettings d;
  protected ImportStore store;
  protected ReferenceFactory refFactory;
  protected static NormalizerConfig cfg;
  private static ImportStoreFactory importStoreFactory;


  @BeforeClass
  public static void initNeo() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    importStoreFactory = new ImportStoreFactory(cfg);
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      store.close();
    }
  }

  @AfterClass
  public static void shutdown() throws Exception {
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  protected DataInserter setup(String resource) {
    URL url = getClass().getResource(resource);
    try {
      return setup(Paths.get(url.toURI()));
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  protected DataInserter setup(Path path) {
    try {
      d = new DatasetWithSettings();
      d.setKey(1);
      store = importStoreFactory.create(d.getKey(), 1);
      refFactory = new ReferenceFactory(d.getKey(), store.references(), null);

      return newInserter(path, d.getSettings());
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public abstract DataInserter newInserter(Path resource, DatasetSettings settings) throws IOException;
  
}
