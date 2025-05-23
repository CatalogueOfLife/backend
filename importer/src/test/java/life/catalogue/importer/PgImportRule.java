package life.catalogue.importer;

import life.catalogue.TestUtils;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.TempFile;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;

import org.gbif.nameparser.api.NomCode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectIntImmutablePair;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Imports the given datasets from the test resources
 * into postgres.
 *
 * Requires a running postgres instance which is normally provided via the PgSetupRule ClassRule.
 */
public class PgImportRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(PgImportRule.class);
  
  static final User IMPORT_USER = new User();
  static {
    IMPORT_USER.setUsername("importator");
    IMPORT_USER.setFirstname("Tim");
    IMPORT_USER.setLastname("Tester");
    IMPORT_USER.setEmail("tim.test@mailinator.com");
    IMPORT_USER.getRoles().add(User.Role.ADMIN);
  }

  private final Validator validator;
  private DatasetDao ddao;

  private NeoDb store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private DatasetWithSettings dataset;
  private final TestResource[] datasets;
  private Pair<DataFormat, String> colImportSource;
  private final Map<Pair<DataFormat, Integer>, Integer> datasetKeyMap = new HashMap<>();
  private final TempFile sourceDir;
  private NameIndex nidx = NameIndexFactory.passThru();

  public static List<TestResource> buildTestResources(Object... params) {
    List<TestResource> resources = new ArrayList<>();
    DatasetOrigin origin = DatasetOrigin.PROJECT;
    DataFormat format = null;
    NomCode code = null;
    DatasetType type = DatasetType.OTHER;
    String resourceFile = null;
    for (Object p : params) {
      if (p instanceof DataFormat) {
        format = (DataFormat) p;
      } else if (p instanceof NomCode) {
        code = (NomCode) p;
      } else if (p instanceof DatasetOrigin) {
        origin = (DatasetOrigin) p;
      } else if (p instanceof DatasetType) {
        type = (DatasetType) p;
      } else if (p instanceof String) {
        resourceFile = (String) p;
      } else if (p instanceof Integer) {
        resources.add(new TestResource((Integer)p, resourceFile, origin, Preconditions.checkNotNull(format), code, type));
      }
    }
    return resources;
  }

  public static PgImportRule create(Object... params) {
    List<TestResource> resources = buildTestResources(params);
    return new PgImportRule(resources.toArray(new TestResource[0]));
  }

  public PgImportRule(TestResource... datasets) {
    this.datasets = datasets;
    try {
      sourceDir = TempFile.directory();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }
  
  public static class TestResource {
    public final int key;
    public final DatasetOrigin origin;
    public final DataFormat format;
    public final NomCode code;
    public final DatasetType type;
    final String resourceFile;

    private TestResource(int key, String resourceFile, DatasetOrigin origin, DataFormat format, NomCode code, DatasetType type) {
      this.key = key;
      this.resourceFile = resourceFile;
      this.origin = origin;
      this.format = Preconditions.checkNotNull(format);
      this.code = code;
      this.type = type;
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

  public void setColImportSource(DataFormat format, String resourceFile) {
    this.colImportSource = Pair.of(format, resourceFile);
  }

  public void setNidx(NameIndex nidx) {
    this.nidx = nidx;
  }

  @Override
  public void before() throws Throwable {
    LOG.info("run PgImportRule with {} datasets{}", datasets.length, colImportSource==null ? "" : " and import COL from " + colImportSource.second());
    super.before();
    ddao = new DatasetDao(SqlSessionFactoryRule.getSqlSessionFactory(), null, null, validator, TestUtils.mockedBroker());

    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(UserMapper.class).create(IMPORT_USER);
    }

    if (colImportSource != null) {
      normalizeAndImportCOL();
    }
    for (TestResource tr : datasets) {
      normalizeAndImport(tr);
      Pair<DataFormat, Integer> key = new ObjectIntImmutablePair<>(tr.format, tr.key);
      datasetKeyMap.put(key, dataset.getKey());
    }
  }
  
  @Override
  public void after() {
    super.after();
    if (store != null) {
      store.closeAndDelete();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
    if (sourceDir.exists()) {
      FileUtils.deleteQuietly(sourceDir.file);
    }
  }

  public Map<Pair<DataFormat, Integer>, Integer> getDatasetKeyMap() {
    return datasetKeyMap;
  }

  public Integer datasetKey(int key, DataFormat format) {
    return datasetKeyMap.get(new ObjectIntImmutablePair<>(format, key));
  }

  void normalizeAndImportCOL() throws Exception {
    dataset = ddao.getWithSettings(Datasets.COL);
    dataset.setDataFormat(colImportSource.first());

    FileUtils.cleanDirectory(sourceDir.file);
    Resources.copy(colImportSource.second(), new File(sourceDir.file, "textree.txt"));
    Path source = sourceDir.file.toPath();
    normalizeAndImportDataset(source);

  }
  void normalizeAndImport(TestResource tr) throws Exception {
    String title;
    Path source;
    if (tr.resourceFile == null) {
      var url = getClass().getResource("/" + tr.format.getFilename().toLowerCase() + "/" + tr.key);
      source = Paths.get(url.toURI());
      title = String.format("%s-%s", tr.format, tr.key);
    } else {
      FileUtils.cleanDirectory(sourceDir.file);
      Resources.copy(tr.resourceFile, new File(sourceDir.file, "textree.txt"));
      source = sourceDir.file.toPath();
      title = tr.resourceFile;
    }
    dataset = new DatasetWithSettings();
    dataset.setCreatedBy(IMPORT_USER.getKey());
    dataset.setModifiedBy(IMPORT_USER.getKey());
    dataset.setDataFormat(tr.format);
    dataset.setType(tr.type);
    dataset.setOrigin(tr.origin);
    dataset.setCode(tr.code);
    dataset.setTitle("Test Dataset " + title);
    // this creates a new key, usually above 1000!
    ddao.create(dataset, IMPORT_USER.getKey());

    normalizeAndImportDataset(source);
  }

  private void normalizeAndImportDataset(Path source) throws Exception {
    // normalize
    store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
    Normalizer norm = new Normalizer(dataset, store, source, nidx, ImageService.passThru(), validator, null);
    norm.call();

    // import into postgres
    store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
    PgImport importer = new PgImport(1, dataset, IMPORT_USER.getKey(), store, SqlSessionFactoryRule.getSqlSessionFactory(), icfg, ddao, NameUsageIndexService.passThru());
    importer.call();
  }
}
