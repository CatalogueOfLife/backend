package life.catalogue.dao;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.event.BrokerConfig;
import life.catalogue.event.EventBroker;
import life.catalogue.event.EventBrokers;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetInfoCacheTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.apple();

  @Test
  public void origin() {
    assertEquals(DatasetOrigin.PROJECT, DatasetInfoCache.CACHE.info(3).origin);
    assertEquals(DatasetOrigin.PROJECT, DatasetInfoCache.CACHE.info(11).origin);
    assertEquals(DatasetOrigin.EXTERNAL, DatasetInfoCache.CACHE.info(12).origin);
  }

  @Test(expected = NotFoundException.class)
  public void notFound() {
    DatasetInfoCache.CACHE.info(999);
  }

  static void await(BooleanSupplier condition) throws InterruptedException {
    for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
      TimeUnit.MILLISECONDS.sleep(25);
    }
    assertTrue(condition.getAsBoolean());
  }

  /**
   * The cache is updated from the facts an event carries, delivered through postgres.
   */
  @Test
  public void updatedThroughBroker() throws Exception {
    try (EventBroker bus = EventBrokers.build(new BrokerConfig(), SqlSessionFactoryRule.getCfg(), SqlSessionFactoryRule.getSqlSessionFactory())) {
      bus.register(DatasetInfoCache.CACHE);
      bus.start();

      var info = DatasetInfoCache.CACHE.info(3);
      assertFalse(info.deleted);

      Dataset d;
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
        d = session.getMapper(DatasetMapper.class).get(3);
      }

      // a changed publisher
      final UUID publisher = UUID.randomUUID();
      Dataset d2 = new Dataset(d);
      d2.setGbifPublisherKey(publisher);
      bus.publish(DatasetChanged.changed(d2, d, 1));
      await(() -> publisher.equals(DatasetInfoCache.CACHE.info(3).publisherKey));

      bus.publish(DatasetChanged.deleted(d, 1));
      await(() -> DatasetInfoCache.CACHE.info(3, true).deleted);
      assertThrows(NotFoundException.class, () -> DatasetInfoCache.CACHE.info(3));
    } finally {
      DatasetInfoCache.CACHE.clear();
    }
  }
}
