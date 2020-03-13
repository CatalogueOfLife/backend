package life.catalogue.db.mapper;

import org.apache.ibatis.annotations.Select;

/**
 * Provides a simple read-only query to test database connectivity.
 * This query touches no tables but runs {@code SELECT 1} to verify that the database is reachable.
 */
public interface Ping {
  /**
   * Pings the database.
   *
   * @return the Answer to the Ultimate Question of Life, The Universe, and Everything.
   */
  @Select("SELECT 42")
  public int ping();
}