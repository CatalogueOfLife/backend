package life.catalogue.cache;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class UsageCacheTest {
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.tree();

  @Test
  public void withClassification() {
    var cache = UsageCache.hashMap();
    var key = DSID.of(testDataRule.testData.key, "s11");

    assertFalse(cache.contains(key));
    assertNull(cache.get(key));

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var loader = new CacheLoader.Mybatis(session);
      var snp = loader.load(key);
      var sncl = cache.withClassification(testDataRule.testData.key, snp, loader);
      assertEquals(6, sncl.getClassification().size());
    }
  }
}