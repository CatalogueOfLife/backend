package life.catalogue.db;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.session.*;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Factory that always throws an exception when a session is requested.
 */
public class EmptySqlSessionFactory implements SqlSessionFactory {

  public static class EmptySqlSession implements SqlSession {

    @Override
    public <T> T selectOne(String statement) {
      return null;
    }

    @Override
    public <T> T selectOne(String statement, Object parameter) {
      return null;
    }

    @Override
    public <E> List<E> selectList(String statement) {
      return List.of();
    }

    @Override
    public <E> List<E> selectList(String statement, Object parameter) {
      return List.of();
    }

    @Override
    public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
      return List.of();
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, String mapKey) {
      return Map.of();
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey) {
      return Map.of();
    }

    @Override
    public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
      return Map.of();
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement) {
      return null;
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement, Object parameter) {
      return null;
    }

    @Override
    public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
      return null;
    }

    @Override
    public void select(String statement, Object parameter, ResultHandler handler) {

    }

    @Override
    public void select(String statement, ResultHandler handler) {

    }

    @Override
    public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {

    }

    @Override
    public int insert(String statement) {
      return 0;
    }

    @Override
    public int insert(String statement, Object parameter) {
      return 0;
    }

    @Override
    public int update(String statement) {
      return 0;
    }

    @Override
    public int update(String statement, Object parameter) {
      return 0;
    }

    @Override
    public int delete(String statement) {
      return 0;
    }

    @Override
    public int delete(String statement, Object parameter) {
      return 0;
    }

    @Override
    public void commit() {

    }

    @Override
    public void commit(boolean force) {

    }

    @Override
    public void rollback() {

    }

    @Override
    public void rollback(boolean force) {

    }

    @Override
    public List<BatchResult> flushStatements() {
      return List.of();
    }

    @Override
    public void close() {

    }

    @Override
    public void clearCache() {

    }

    @Override
    public Configuration getConfiguration() {
      return null;
    }

    @Override
    public <T> T getMapper(Class<T> type) {
      return null;
    }

    @Override
    public Connection getConnection() {
      return null;
    }
  }

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
    return newSession();
  }

  @Override
  public SqlSession openSession(TransactionIsolationLevel level) {
    return newSession();
  }

  @Override
  public SqlSession openSession(ExecutorType execType) {
    return newSession();
  }

  @Override
  public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
    return newSession();
  }

  @Override
  public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
    return newSession();
  }

  @Override
  public SqlSession openSession(ExecutorType execType, Connection connection) {
    return newSession();
  }

  @Override
  public Configuration getConfiguration() {
    throw exception();
  }

  private UnsupportedOperationException exception() {
    return new UnsupportedOperationException("No sql session supported.");
  }
  private SqlSession newSession() {
    return new EmptySqlSession();
  }

}
