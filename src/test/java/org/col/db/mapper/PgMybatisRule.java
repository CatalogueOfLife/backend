package org.col.db.mapper;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jersey.repackaged.com.google.common.base.Throwables;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.commands.initdb.InitDbCmd;
import org.col.db.MybatisBundle;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

/**
 * A junit test rule that starts up an {@link EmbeddedPostgres} server together with a
 * {@link HikariDataSource} and stops both at the end.
 * The rule was designed to share the server across all tests of a test class if it runs as
 * a static {@link org.junit.ClassRule}.
 *
 * It can even be used to share the same postgres server across several test classes
 * if it is used in as a {@link org.junit.ClassRule} in a TestSuite.
 */
public class PgMybatisRule implements TestRule {
  private static EmbeddedPostgres postgres;
  private static HikariDataSource dataSource;
  private static SqlSession session;
  private boolean startedHere = false;

  public <T> T getMapper(Class<T> mapperClazz) {
    return session.getMapper(mapperClazz);
  }

  public static Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  public void commit() {
    session.commit();
  }

  @Override
  public Statement apply(Statement base, Description description) {
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


  private void before() {
    if (postgres == null) {
      startDb();
      initDb();
      initMyBatis();
    }
  }

  private void startDb() {
    System.out.println("Starting Postgres");
    try {
      postgres = new EmbeddedPostgres(Version.Main.V9_6);
      startedHere = true;
      // assigned to some free port
      ServerSocket socket = new ServerSocket(0);
      final String user = "col";
      final String password = "species2000";

      Instant start = Instant.now();
      //      System.out.println("Start postgres on port "+socket.getLocalPort());
      final String url = postgres.start("localhost", socket.getLocalPort(), "col", user, password);
      System.out.format("Pg startup time: %s ms\n", Duration.between(start, Instant.now()).toMillis());

      HikariConfig hikari = new HikariConfig();
      hikari.setJdbcUrl(url);
      //hikari.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource");
      hikari.setUsername(user);
      hikari.setPassword(password);
      hikari.setMaximumPoolSize(2);
      hikari.setMinimumIdle(1);
      dataSource = new HikariDataSource(hikari);

    } catch (Exception e) {
      if (dataSource != null) {
        dataSource.close();
      }
      if (postgres != null) {
        postgres.stop();
      }
      Throwables.propagate(e);
    }
  }

  private void initDb() {
    try (Connection con = dataSource.getConnection()) {
      System.out.println("Init empty database schema\n");
      ScriptRunner runner = new ScriptRunner(con);
      runner.runScript(Resources.getResourceAsReader(InitDbCmd.SCHEMA_FILE));
      con.commit();

    } catch (SQLException | IOException e) {
      Throwables.propagate(e);
    }
  }

  private void initMyBatis() {
    SqlSessionFactory factory = MybatisBundle.configure(dataSource, "test");
    session = factory.openSession();
  }

  private void after() {
    if (startedHere) {
      System.out.println("Stopping Postgres");
      dataSource.close();
      postgres.stop();
    }
  }

}
