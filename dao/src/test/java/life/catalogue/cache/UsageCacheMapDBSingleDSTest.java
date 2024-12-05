package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.common.io.TempFile;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.apache.commons.io.FileUtils;
import org.junit.*;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class UsageCacheMapDBSingleDSTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.tree();

  @Test
  public void create() throws IOException {
    UsageCacheMapDBSingleDS cache = null;
    try (var tmpF = new TempFile()) {
      FileUtils.deleteQuietly(tmpF.file);
      cache = UsageCacheMapDBSingleDS.createStarted(tmpF.file, 8, testDataRule.testData.key, SqlSessionFactoryRule.getSqlSessionFactory());
      assertEquals((int)testDataRule.testData.key, cache.getDatasetKey());
      assertEquals(24, cache.size());
      var key = DSID.of(testDataRule.testData.key, "t23");
      var u = cache.get(key);
      assertEquals("Canis adustus", u.getName());
      var cl = cache.getClassification(key, null);
      assertEquals(7, cl.size());
      cache.stop();

      cache.start();
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