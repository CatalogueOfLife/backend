package org.col.db;


import org.col.config.ConfigTestUtils;
import org.col.config.PgConfig;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.annotation.Nullable;
import java.sql.Connection;

/**
 * A TestRule for Database driven Integration tests executing some dbSetup file beforehand.
 */
public class DbTestRule implements TestRule {

  private final String tsvFolder;
  private Connection connection;
  private PgConfig pgConfig;

  /**
   * Prepares an empty CLB db before any test is run, truncating tables and resetting sequence counters.
   */
  public static DbTestRule empty() {
    return new DbTestRule(null);
  }

  /**
   * Prepares a squirrels test db before any test is run, adding data and adjusting sequence counters.
   */
  public static DbTestRule puma() {
    return new DbTestRule("puma");
  }

  public static DbTestRule quatsch() {
    return new DbTestRule("quatsch");
  }

  /**
   * @param tsvFolder the optional unqualified filename within the dbUnit package to be used in setting up
   *                  the db
   */
  private DbTestRule(@Nullable String tsvFolder) {
    this.tsvFolder = tsvFolder;
    pgConfig = ConfigTestUtils.testConfig();
  }

  public PgConfig getPgConfig() {
    return pgConfig;
  }

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {

      @Override
      public void evaluate() throws Throwable {
        try {
          before();
          base.evaluate();
        } finally {
          after();
        }
      }
    };
  }

  /**
   * @return the existing, single db connection. Keep this open, it will be closed by this rule after the tests have run.
   */
  public Connection getConnection() {
    return connection;
  }

  public void before() throws Exception {
    SLF4JBridgeHandler.install();
    connection = PgLoader.connect(pgConfig);
    connection.setAutoCommit(false);
    if (tsvFolder != null) {
      PgLoader.load(connection, tsvFolder);
    } else {
      PgLoader.truncate(connection, "quatsch", true);
    }
    connection.setAutoCommit(true);
  }

  public void after() throws Exception {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

}
