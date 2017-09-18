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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public class MapperTestBaseNoRule<T> {
  private static EmbeddedPostgres postgres;
  private static HikariDataSource dataSource;
  private static SqlSession session;

  T mapper;

  //@Rule
  //public DbInitRule dbInitRule = DbInitRule.empty();

  public MapperTestBaseNoRule(Class<T> mapperClazz) {
    //mapper = session.getMapper(mapperClazz);
  }

  public void commit() {
    session.commit();
  }


  @BeforeClass
  public static void before() throws Throwable {
    startDb();
    initDb();
    initMyBatis();
  }

  private static void startDb() {
    System.out.println("Starting Postgres");
    try {
      postgres = new EmbeddedPostgres(Version.V9_6_3);
      // assigned to some free port
      ServerSocket socket = new ServerSocket(0);
      final String database = "col";
      final String user = "col";
      final String password = "species2000";

      Instant start = Instant.now();
      System.out.println("Start postgres on port "+socket.getLocalPort());
      final String url = postgres.start("localhost", socket.getLocalPort(), database, user, password);
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
      System.err.println("Pg startup error: " + e.getMessage());
      e.printStackTrace();

      if (dataSource != null) {
        dataSource.close();
      }
      if (postgres != null) {
        postgres.stop();
      }
      Throwables.propagate(e);
    }
  }

  private static void initDb() {
    try (Connection con = dataSource.getConnection()) {
      System.out.println("Init empty database schema\n");
      ScriptRunner runner = new ScriptRunner(con);
      runner.runScript(Resources.getResourceAsReader(InitDbCmd.SCHEMA_FILE));
      con.commit();

    } catch (SQLException | IOException e) {
      Throwables.propagate(e);
    }
  }

  private static void initMyBatis() {
    SqlSessionFactory factory = MybatisBundle.configure(dataSource, "test");
    session = factory.openSession();
  }

  @AfterClass
  public static void after() {
    System.out.println("Stopping Postgres");
    dataSource.close();
    postgres.stop();
  }

}