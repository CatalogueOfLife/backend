package org.col.db.mapper;

import jersey.repackaged.com.google.common.base.Throwables;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;
import ru.yandex.qatools.embed.postgresql.distribution.Version;

import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;

/**
 * A reusable base class for all mybatis mapper tests that takes care of postgres & mybatis.
 * It offers a mapper to test in the implementing subclass.
 */
public class MapperTestBaseNoRule<T> {
  private static EmbeddedPostgres postgres;

  T mapper;

  //@Rule
  //public DbInitRule dbInitRule = DbInitRule.empty();

  public MapperTestBaseNoRule(Class<T> mapperClazz) {
    //mapper = session.getMapper(mapperClazz);
  }

  public void commit() {
  }


  @BeforeClass
  public static void before() throws Throwable {
    startDb();
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

    } catch (Exception e) {
      System.err.println("Pg startup error: " + e.getMessage());
      e.printStackTrace();

      if (postgres != null) {
        postgres.stop();
      }
      Throwables.propagate(e);
    }
  }

  @AfterClass
  public static void after() {
    System.out.println("Stopping Postgres");
    postgres.stop();
  }

}