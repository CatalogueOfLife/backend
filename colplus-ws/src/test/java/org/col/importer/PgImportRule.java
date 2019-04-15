package org.col.importer;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.col.command.initdb.InitDbCmd;
import org.col.config.ImporterConfig;
import org.col.config.NormalizerConfig;
import org.col.importer.neo.NeoDb;
import org.col.importer.neo.NeoDbFactory;
import org.col.matching.NameIndexFactory;
import org.col.api.model.Dataset;
import org.col.api.vocab.DataFormat;
import org.col.db.PgSetupRule;
import org.col.db.mapper.DatasetMapper;
import org.col.db.mapper.TestDataRule;

/**
 * Imports the given datasets from the test resources
 * into postgres.
 *
 * Requires a running postgres instance which is normally provided via the PgSetupRule ClassRule.
 */
public class PgImportRule extends TestDataRule {
  
  private NeoDb store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private Dataset dataset;
  private final TestResource[] datasets;
  private final Map<TestResource, Integer> datasetKeyMap = new HashMap<>();
  
  public static PgImportRule create(Object... params) {
    List<TestResource> resources = new ArrayList<>();
    DataFormat format = null;
    for (Object p : params) {
      if (p instanceof DataFormat) {
        format = (DataFormat) p;
      } else if (p instanceof Integer) {
        resources.add(new TestResource((Integer)p, format));
      }
    }
    return new PgImportRule(resources.toArray(new TestResource[0]));
  }

  public PgImportRule(TestResource... datasets) {
    super(TestData.NONE);
    this.datasets = datasets;
  }
  
  public static class TestResource {
    public final int key;
    public final DataFormat format;
  
    public TestResource(int key, DataFormat format) {
      this.key = key;
      this.format = Preconditions.checkNotNull(format);
    }
  
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TestResource that = (TestResource) o;
      return key == that.key && format == that.format;
    }
  
    @Override
    public int hashCode() {
      return Objects.hash(key, format);
    }
  }
  
  @Override
  protected void before() throws Throwable {
    super.before();
  
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    InitDbCmd.setupStandardPartitions(getSqlSession());
    commit();
    
    for (TestResource tr : datasets) {
      normalizeAndImport(tr.key, tr.format);
      datasetKeyMap.put(tr, dataset.getKey());
    }
  }
  
  @Override
  protected void after() {
    super.after();
    if (store != null) {
      store.closeAndDelete();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
  
  public Integer datasetKey(TestResource res) {
    return datasetKeyMap.get(res);
  }
  
  public Integer datasetKey(int key, DataFormat format) {
    return datasetKeyMap.get(new TestResource(key, format));
  }

  void normalizeAndImport(int key, DataFormat format) throws Exception {
    URL url = getClass().getResource("/" + format.name().toLowerCase() + "/" + key);
    dataset = new Dataset();
    dataset.setContributesTo(null);
    dataset.setCreatedBy(TestDataRule.TEST_USER.getKey());
    dataset.setModifiedBy(TestDataRule.TEST_USER.getKey());
    dataset.setDataFormat(format);
    Path source = Paths.get(url.toURI());

    try {
      // insert trusted dataset
      dataset.setTitle("Test Dataset " + source.toString());
      
      SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
      // this creates a new key, usually above 2000!
      session.getMapper(DatasetMapper.class).create(dataset);
      session.commit();
      session.close();
      
      // normalize
      store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
      store.put(dataset);
      Normalizer norm = new Normalizer(store, source, NameIndexFactory.passThru());
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
      PgImport importer = new PgImport(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(), icfg);
      importer.call();
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
}
