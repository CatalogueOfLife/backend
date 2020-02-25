package life.catalogue.importer;

import com.google.common.io.Files;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.command.initdb.InitDbCmd;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.TestDataRule;
import life.catalogue.db.mapper.VerbatimRecordMapper;
import life.catalogue.img.ImageService;
import life.catalogue.importer.neo.NeoDb;
import life.catalogue.importer.neo.NeoDbFactory;
import life.catalogue.matching.NameIndexFactory;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;

import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * Checks for unit test ColDP archives under resources/integrity-checks/
 */
public class IntegrityChecksIT {
  static final AuthorshipNormalizer aNormalizer = AuthorshipNormalizer.createWithAuthormap();
  
  private NeoDb store;
  private NormalizerConfig cfg;
  private ImporterConfig icfg = new ImporterConfig();
  private Dataset dataset;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public TestDataRule testDataRule = TestDataRule.empty();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();
  
  @Before
  public void initCfg() {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
    dataset = new Dataset();
    dataset.setType(DatasetType.OTHER);
    dataset.setOrigin(DatasetOrigin.UPLOADED);
    dataset.setCreatedBy(TestDataRule.TEST_USER.getKey());
    dataset.setModifiedBy(TestDataRule.TEST_USER.getKey());

    InitDbCmd.setupStandardPartitions(testDataRule.getSqlSession());
    testDataRule.commit();
 }
  
  @After
  public void cleanup() {
    if (store != null) {
      store.closeAndDelete();
      FileUtils.deleteQuietly(cfg.archiveDir);
      FileUtils.deleteQuietly(cfg.scratchDir);
    }
  }
  
  void normalizeAndImport(String key) {
    URL url = getClass().getResource("/integrity-checks/" + key);
    dataset.setDataFormat(DataFormat.COLDP);
    try {
      // insert trusted dataset
      dataset.setTitle("Integrity check " + key);
      
      SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true);
      // this creates a new key, usually 2000!
      session.getMapper(DatasetMapper.class).create(dataset);
      session.commit();
      session.close();
      
      // normalize
      store = NeoDbFactory.create(dataset.getKey(), 1, cfg);
      store.put(dataset);
      Normalizer norm = new Normalizer(store, Paths.get(url.toURI()), NameIndexFactory.memory(PgSetupRule.getSqlSessionFactory(), aNormalizer), ImageService.passThru());
      norm.call();
      
      // import into postgres
      store = NeoDbFactory.open(dataset.getKey(), 1, cfg);
      PgImport importer = new PgImport(dataset.getKey(), store, PgSetupRule.getSqlSessionFactory(), aNormalizer, icfg);
      importer.call();
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  DatasetImport metrics() {
    return new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo())
        .generateMetrics(dataset.getKey(), Users.TESTER );
  }
  
  
  @Test
  //@Ignore("depends on duplicate detection working")
  public void testA1() throws Exception {
    normalizeAndImport("A1");

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {

      NameMapper nm = session.getMapper(NameMapper.class);
      Name m = nm.get(new DSIDValue<>(dataset.getKey(), "1"));
      VerbatimRecordMapper v = session.getMapper(VerbatimRecordMapper.class);
      VerbatimRecord vr = v.get(DSID.vkey(m));

      // missing species epithet
      assertTrue(vr.hasIssue(Issue.INDETERMINED));

    }
  }
  
}
