package life.catalogue.dao;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;

import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.eventbus.EventBus;

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

  @Test
  public void deletedEvent() throws InterruptedException {
    EventBus bus = new EventBus();
    bus.register(DatasetInfoCache.CACHE);

    var info = DatasetInfoCache.CACHE.info(3);
    assertFalse(info.deleted);

    Dataset d;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      d = session.getMapper(DatasetMapper.class).get(3);
    }

    bus.post(DatasetChanged.created(d));
    info = DatasetInfoCache.CACHE.info(3);
    assertFalse(info.deleted);

    bus.post(DatasetChanged.deleted(d));
    TimeUnit.MILLISECONDS.sleep(10); // give the event a little bit of time
    info = DatasetInfoCache.CACHE.info(3, true);
    assertTrue(info.deleted);
  }}