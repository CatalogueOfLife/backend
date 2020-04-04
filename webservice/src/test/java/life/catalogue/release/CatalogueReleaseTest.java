package life.catalogue.release;

import com.google.common.io.Files;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.TestDataRule;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.fail;

public class CatalogueReleaseTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();
  
  WsServerConfig cfg;
  DatasetImportDao diDao;
  AcExporter exp;
  Dataset d;
  
  @Before
  public void init()  {
    cfg = new WsServerConfig();
    cfg.db = PgSetupRule.getCfg();
    cfg.downloadDir = Files.createTempDir();
    cfg.normalizer.scratchDir  = Files.createTempDir();
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    exp = new AcExporter(cfg, PgSetupRule.getSqlSessionFactory());
    
    // dataset needs to be a managed one
    try (SqlSession s = PgSetupRule.getSqlSessionFactory().openSession()) {
      DatasetMapper dm = s.getMapper(DatasetMapper.class);
      d = dm.get(TestDataRule.TestData.APPLE.key);
    }
  }
  
  @Test
  public void release() throws Exception {
    CatalogueRelease release = buildRelease();
    release.run();
  }
  
  private CatalogueRelease buildRelease() {
    return CatalogueRelease.release(PgSetupRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), exp, diDao, d.getKey(), Users.TESTER);
  }
  
  @Test
  public void releaseConcurrently() throws Exception {
    Thread t1 = new Thread(buildRelease());
    t1.start();
  
    try {
      buildRelease();
      fail("Parallel releases should not be allowed!");
    } catch (IllegalStateException e) {
      // expected
    }
    // wait for release to be done and run another one
    t1.join();
  
    Thread t2 = new Thread(buildRelease());
    t2.start();
    t2.join();
  }
  
}