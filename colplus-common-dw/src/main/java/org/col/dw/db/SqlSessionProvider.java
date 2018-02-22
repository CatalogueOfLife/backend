package org.col.dw.db;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;

/**
 * Injection provider for {@link org.apache.ibatis.session.SqlSession}s.
 * Using the default {@link #binder(org.apache.ibatis.session.SqlSessionFactory) binder},
 * this will provide an SqlSession scoped to an HTTP request,
 * which will automatically be closed (and rolled back -- transactions are your problem)
 * when the request completes.
 */
public class SqlSessionProvider implements Factory<SqlSession> {
  public static class Binder extends AbstractBinder {
    private final SqlSessionProvider sqlSessionProvider;

    public Binder(SqlSessionFactory sqlSessionFactory) {
      sqlSessionProvider = new SqlSessionProvider(sqlSessionFactory);
    }

    @Override
    protected void configure() {
      bindFactory(sqlSessionProvider).to(SqlSession.class).in(RequestScoped.class);
    }
  }

  /**
   * Given an SqlSessionFactory, provide SqlSessions from it for requests that need them.
   *
   * @param sqlSessionFactory the factory to obtain sessions from.
   * @return an HK2 binder providing an SqlSession for each request.
   */
  public static Binder binder(SqlSessionFactory sqlSessionFactory) {
    return new Binder(sqlSessionFactory);
  }

  private final SqlSessionFactory sessionFactory;

  public SqlSessionProvider(SqlSessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public SqlSession provide() {
    return sessionFactory.openSession(false);
  }

  @Override
  public void dispose(SqlSession sqlSession) {
    sqlSession.close();
  }
}