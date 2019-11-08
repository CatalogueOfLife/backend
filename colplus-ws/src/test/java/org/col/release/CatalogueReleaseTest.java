package org.col.release;

import com.google.common.io.Files;
import org.col.WsServerConfig;
import org.col.api.vocab.Users;
import org.col.dao.DatasetImportDao;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
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
  
  @Before
  public void init()  {
    cfg = new WsServerConfig();
    cfg.db = PgSetupRule.getCfg();
    cfg.downloadDir = Files.createTempDir();
    cfg.normalizer.scratchDir  = Files.createTempDir();
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    exp = new AcExporter(cfg, PgSetupRule.getSqlSessionFactory());
  }
  
  @Test
  public void release() throws Exception {
    CatalogueRelease release = buildRelease();
    release.run();
  }
  
  private CatalogueRelease buildRelease() {
    return CatalogueRelease.release(PgSetupRule.getSqlSessionFactory(), exp, diDao, TestDataRule.TestData.APPLE.key, Users.TESTER);
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