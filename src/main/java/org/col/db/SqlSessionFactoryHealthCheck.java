package org.col.db;


import com.codahale.metrics.health.HealthCheck;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.db.mapper.Ping;

/**
 * {@link Ping}s the database to check its health. Any non-exceptional response,
 * in any amount of time, is treated as a healthy response.
 * Any exceptional response is treated as an unhealthy response.
 */
public class SqlSessionFactoryHealthCheck extends HealthCheck {
  private final SqlSessionFactory sqlSessionFactory;

  public SqlSessionFactoryHealthCheck(SqlSessionFactory sqlSessionFactory) {
    this.sqlSessionFactory = sqlSessionFactory;
  }

  @Override
  protected Result check() throws Exception {
    try (SqlSession session = sqlSessionFactory.openSession()) {
      Ping mapper = session.getMapper(Ping.class);
      mapper.ping();
      return Result.healthy();
    } catch (Exception e) {
      return Result.unhealthy(e);
    }
  }
}