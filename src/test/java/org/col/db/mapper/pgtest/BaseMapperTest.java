package org.gbif.datarepo.persistence.mappers;

import org.gbif.datarepo.persistence.DataPackageMyBatisModule;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

import com.google.inject.Guice;
import com.google.inject.Injector;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

/**
 * Base class to test MyBatis mappers using an embedded Postgres server.
 */
public class BaseMapperTest {

  //Embedded postgres server
  private static EmbeddedPostgres embeddedPostgres;

  protected static String jdbcUrl;



  /**
   * Initializes the Postgres server and database.
   */
  @BeforeClass
  public static void initDB() throws IOException {
    embeddedPostgres = new EmbeddedPostgres(Version.V9_4_10);
    jdbcUrl = embeddedPostgres.start("localhost", new ServerSocket(0).getLocalPort(), "testdb", "user", "password");
    runLiquibase();
  }

  @Before
  public void clearDB() {
    try {
      Class.forName("org.postgresql.Driver");
      try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
        connection.prepareStatement("DELETE FROM data_package").execute();
      }
    } catch (ClassNotFoundException | SQLException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Executes the liquibase master.xml change logs in the context ddl.
   */
  private static void runLiquibase() {
    try {
      Class.forName("org.postgresql.Driver");
      try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
        Liquibase liquibase = new Liquibase("liquibase/master.xml", new ClassLoaderResourceAccessor(),
                                            new JdbcConnection(connection));
        liquibase.dropAll();
        liquibase.update("ddl");
      }
    } catch (ClassNotFoundException | SQLException | LiquibaseException ex) {
      throw new IllegalStateException(ex);
    }
  }

  /**
   * Stops the Postgres embedded server.
   */
  @AfterClass
  public static void tearDown() {
    Optional.ofNullable(embeddedPostgres).ifPresent(EmbeddedPostgres::stop);
  }

  /**
   * Creates the MyBatis Guice injector.
   */
  public static Injector buildInjector() {
    Properties properties = new Properties();
    properties.setProperty("poolName", "datapackagesTest");
    properties.setProperty("maximumPoolSize", "1");
    properties.setProperty("minimumIdle", "1");
    properties.setProperty("idleTimeout", "1000");
    properties.setProperty("connectionTimeout", "500");
    properties.setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource");
    properties.setProperty("dataSource.url", jdbcUrl);
    return Guice.createInjector(new DataPackageMyBatisModule(properties, null, null));
  }

}
