package life.catalogue.db;

import org.apache.ibatis.session.*;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A sql session factory that set the postgres search_path before any session is returned.
 * Beware that this search_path remains on the db connection if a db pool is used
 * and the connection is reused!
 */
public class SqlSessionFactoryWithPath implements SqlSessionFactory {
  private final SqlSessionFactory factory;
  private final String schema;

  public SqlSessionFactoryWithPath(SqlSessionFactory factory, String schema) {
    this.factory = factory;
    this.schema = schema;
  }

  private SqlSession setPath(SqlSession session) {
    if (schema != null) {
      try {
        var st = session.getConnection().createStatement();
        st.execute(String.format("SET search_path TO %s, public", schema));
      } catch (SQLException e) {
        throw new RuntimeException(e);
      }
    }
    return session;
  }

  @Override
  public SqlSession openSession() {
    SqlSession session = factory.openSession();
    return setPath(session);
  }

  @Override
  public SqlSession openSession(boolean b) {
    SqlSession session = factory.openSession(b);
    return setPath(session);
  }

  @Override
  public SqlSession openSession(Connection connection) {
    SqlSession session = factory.openSession(connection);
    return setPath(session);
  }

  @Override
  public SqlSession openSession(TransactionIsolationLevel transactionIsolationLevel) {
    SqlSession session = factory.openSession(transactionIsolationLevel);
    return setPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType) {
    SqlSession session = factory.openSession(executorType);
    return setPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType, boolean b) {
    SqlSession session = factory.openSession(executorType, b);
    return setPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType, TransactionIsolationLevel transactionIsolationLevel) {
    SqlSession session = factory.openSession(executorType, transactionIsolationLevel);
    return setPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType, Connection connection) {
    SqlSession session = factory.openSession(executorType, connection);
    return setPath(session);
  }

  @Override
  public Configuration getConfiguration() {
    return factory.getConfiguration();
  }
}
