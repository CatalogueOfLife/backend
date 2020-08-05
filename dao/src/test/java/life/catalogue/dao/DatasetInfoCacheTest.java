package life.catalogue.dao;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DatasetInfoCacheTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void origin() {
    assertEquals(DatasetOrigin.MANAGED, DatasetInfoCache.CACHE.origin(3));
    assertEquals(DatasetOrigin.MANAGED, DatasetInfoCache.CACHE.origin(11));
    assertEquals(DatasetOrigin.EXTERNAL, DatasetInfoCache.CACHE.origin(12));
  }

  @Test(expected = NotFoundException.class)
  public void notFound() {
    DatasetInfoCache.CACHE.origin(999);
  }
}