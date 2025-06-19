package life.catalogue.db.mapper;

import life.catalogue.api.model.ApiLog;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Test;

import java.time.LocalDateTime;

public class ApiLogsMapperTest {

  @ClassRule
  public static SqlSessionFactoryRule pgRule = new PgSetupRule();

  @Test
  public void create() throws Exception {
    var log = new ApiLog();
    log.setDate(LocalDateTime.now());
    log.setMethod(ApiLog.HttpMethod.GET);
    log.setRequest("/dataset/3LXRC/sector");
    log.setUser(100);
    log.setDatasetKey(23456);
    log.setResponseCode(200);
    log.setAgent("sedrftghj");
    log.setDuration(345678);

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(false)) {
      var mapper = session.getMapper(ApiLogsMapper.class);
      mapper.create(log);
      log.setUser(101);
      log.setDate(LocalDateTime.now());
      mapper.create(log);
      session.commit();
    }
  }
}