package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.common.io.TempFile;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UsageCacheMapDBTest2 {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.tree();

  @Test
  public void create() throws IOException {
    UsageCacheMapDB cache = null;
    try (var tmpF = new TempFile()) {
      FileUtils.deleteQuietly(tmpF.file);
      cache = new UsageCacheMapDB(testDataRule.testData.key, tmpF.file, 8);
      cache.load(SqlSessionFactoryRule.getSqlSessionFactory());

      assertEquals((int)testDataRule.testData.key, cache.getDatasetKey());
      assertEquals(24, cache.size());
      String key = "t23";
      var u = cache.get(key);
      assertEquals("Canis adustus", u.getName());
      var cl = cache.getClassification(key, null);
      assertEquals(7, cl.size());

      assertEquals((int)testDataRule.testData.key, cache.getDatasetKey());
      assertEquals(24, cache.size());
      u = cache.get(key);
      assertEquals("Canis adustus", u.getName());
      cl = cache.getClassification(key, null);
      assertEquals(7, cl.size());

    } finally {
      cache.close();
    }
  }
}