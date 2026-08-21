package life.catalogue.db;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PgConfigTest {

  private static PgConfig cfg() {
    return new PgConfig("localhost", "col", "postgres", "");
  }

  /**
   * The default of 0 must not emit any SET at all so the connection simply inherits
   * whatever idle_in_transaction_session_timeout the postgres server is configured with.
   */
  @Test
  public void idleInTransactionInherited() {
    var cfg = cfg();
    assertEquals(0, cfg.idleInTransactionSessionTimeout);
    assertNull(cfg.hikariConfig().getConnectionInitSql());
  }

  @Test
  public void idleInTransactionExplicitTimeout() {
    var cfg = cfg();
    cfg.idleInTransactionSessionTimeout = 60000;
    assertEquals("SET idle_in_transaction_session_timeout TO 60000;", cfg.hikariConfig().getConnectionInitSql());
  }

  /**
   * A negative value must actively disable the timeout, overriding the server setting.
   * Postgres spells "no timeout" as 0, so -1 has to be translated.
   */
  @Test
  public void idleInTransactionDisabled() {
    var cfg = cfg();
    cfg.idleInTransactionSessionTimeout = -1;
    assertEquals("SET idle_in_transaction_session_timeout TO 0;", cfg.hikariConfig().getConnectionInitSql());
  }

  @Test
  public void lockTimeoutInherited() {
    var cfg = cfg();
    assertEquals(0, cfg.lockTimeout);
    assertNull(cfg.hikariConfig().getConnectionInitSql());
  }

  @Test
  public void lockTimeoutExplicitTimeout() {
    var cfg = cfg();
    cfg.lockTimeout = 5000;
    assertEquals("SET lock_timeout TO 5000;", cfg.hikariConfig().getConnectionInitSql());
  }

  @Test
  public void lockTimeoutDisabled() {
    var cfg = cfg();
    cfg.lockTimeout = -1;
    assertEquals("SET lock_timeout TO 0;", cfg.hikariConfig().getConnectionInitSql());
  }

  @Test
  public void combinedInitSql() {
    var cfg = cfg();
    cfg.workMem = 128;
    cfg.lockTimeout = -1;
    cfg.idleInTransactionSessionTimeout = -1;
    String sql = cfg.hikariConfig().getConnectionInitSql();
    assertTrue(sql, sql.contains("SET work_mem='128MB';"));
    assertTrue(sql, sql.contains("SET lock_timeout TO 0;"));
    assertTrue(sql, sql.contains("SET idle_in_transaction_session_timeout TO 0;"));
  }
}
