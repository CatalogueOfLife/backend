package org.col.pgtest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Base class to test MyBatis mappers using an embedded Postgres server.
 */
public class BMTest {

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
    System.out.println("Postgres has started, yes!");
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
   * Stops the Postgres embedded server.
   */
  @AfterClass
  public static void tearDown() {
    Optional.ofNullable(embeddedPostgres).ifPresent(EmbeddedPostgres::stop);
  }


}
