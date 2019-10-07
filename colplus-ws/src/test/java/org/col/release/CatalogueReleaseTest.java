package org.col.release;

import org.col.api.vocab.Users;
import org.col.dao.DatasetImportDao;
import org.col.dao.TreeRepoRule;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CatalogueReleaseTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.apple();
  
  @Test
  public void release() throws Exception {
    DatasetImportDao diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    CatalogueRelease releaser = CatalogueRelease.release(PgSetupRule.getSqlSessionFactory(), diDao, TestDataRule.TestData.APPLE.key, Users.TESTER);
    Integer releaseKey = releaser.call();
  }
}