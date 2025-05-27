package life.catalogue.dao;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.event.BrokerConfig;
import life.catalogue.event.EventBroker;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
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

  @Test
  public void deletedEvent() throws InterruptedException, IOException {
    var cfg = new BrokerConfig();
    FileUtils.deleteDirectory(new File(cfg.queueDir));
    EventBroker bus = new EventBroker(cfg);
    try {
      bus.register(DatasetInfoCache.CACHE);
      bus.start();

      var info = DatasetInfoCache.CACHE.info(3);
      assertFalse(info.deleted);

      Dataset d;
      try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
        d = session.getMapper(DatasetMapper.class).get(3);
      }

      bus.publish(DatasetChanged.created(d, 1));
      info = DatasetInfoCache.CACHE.info(3);
      assertFalse(info.deleted);

      bus.publish(DatasetChanged.deleted(d, 1));
      TimeUnit.MILLISECONDS.sleep(110); // give the event a little bit of time

      Writer pw = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
      bus.dumpQueue(pw);

      info = DatasetInfoCache.CACHE.info(3, true);
      assertTrue(info.deleted);

    } finally {
      bus.stop();
    }

  }}