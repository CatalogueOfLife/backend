package life.catalogue.db;

import java.sql.Connection;
import java.sql.SQLException;

import com.google.common.base.Preconditions;

import org.apache.ibatis.session.*;

/**
 * A sql session factory that sets the postgres search_path before any session is returned.
 * Beware that this search_path remains on the db connection if a db pool is used
 * and the connection is reused!
 */
public class SqlSessionFactoryWithPath implements SqlSessionFactory {
  private final SqlSessionFactory factory;
  private final String schema;

  public SqlSessionFactoryWithPath(SqlSessionFactory factory, String schema) {
    this.factory = factory;
    this.schema = Preconditions.checkNotNull(schema);
  }

  private SqlSession withSearchPath(SqlSession session) {
    setSearchPath(session.getConnection(), schema);
    return session;
  }

  public static void setSearchPath(Connection c, String schema) {
    try (var st = c.createStatement()) {
      st.execute(String.format("SET search_path TO %s, public", schema));
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public SqlSession openSession() {
    SqlSession session = factory.openSession();
    return withSearchPath(session);
  }

  @Override
  public SqlSession openSession(boolean b) {
    SqlSession session = factory.openSession(b);
    return withSearchPath(session);
  }

  @Override
  public SqlSession openSession(Connection connection) {
    SqlSession session = factory.openSession(connection);
    return withSearchPath(session);
  }

  @Override
  public SqlSession openSession(TransactionIsolationLevel transactionIsolationLevel) {
    SqlSession session = factory.openSession(transactionIsolationLevel);
    return withSearchPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType) {
    SqlSession session = factory.openSession(executorType);
    return withSearchPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType, boolean b) {
    SqlSession session = factory.openSession(executorType, b);
    return withSearchPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType, TransactionIsolationLevel transactionIsolationLevel) {
    SqlSession session = factory.openSession(executorType, transactionIsolationLevel);
    return withSearchPath(session);
  }

  @Override
  public SqlSession openSession(ExecutorType executorType, Connection connection) {
    SqlSession session = factory.openSession(executorType, connection);
    return withSearchPath(session);
  }

  @Override
  public Configuration getConfiguration() {
    return factory.getConfiguration();
  }
}
