package life.catalogue.db;

import org.apache.ibatis.session.*;

import java.sql.Connection;

/**
 * Factory that always throws an exception when a session is requested.
 */
public class EmptySqlSessionFactory implements SqlSessionFactory {
  @Override
  public SqlSession openSession() {
    return openSession(true);
  }

  @Override
  public SqlSession openSession(boolean autoCommit) {
    return openSession(ExecutorType.SIMPLE, true);
  }

  @Override
  public SqlSession openSession(Connection connection) {
    throw exception();
  }

  @Override
  public SqlSession openSession(TransactionIsolationLevel level) {
    throw exception();
  }

  @Override
  public SqlSession openSession(ExecutorType execType) {
    throw exception();
  }

  @Override
  public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
    throw exception();
  }

  @Override
  public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
    throw exception();
  }

  @Override
  public SqlSession openSession(ExecutorType execType, Connection connection) {
    throw exception();
  }

  @Override
  public Configuration getConfiguration() {
    throw exception();
  }

  private UnsupportedOperationException exception() {
    return new UnsupportedOperationException("No sql session supported.");
  }
}
