package life.catalogue.pgcopy;

import java.sql.SQLException;
import java.time.Duration;

import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class PgRule extends ExternalResource {
  private static String PG_VERSION = "15.4";
  private static String DB_NAME = "test";
  private static PostgreSQLContainer<?> PG_CONTAINER;

  @Override
  public void before() throws Throwable {
    PG_CONTAINER = new PostgreSQLContainer<>("postgres:"+PG_VERSION)
      .withReuse(true).withLabel("reuse.tag", "PG_TEST_container")
      .withDatabaseName(DB_NAME);
    PG_CONTAINER.setWaitStrategy(Wait.defaultWaitStrategy().withStartupTimeout(Duration.ofSeconds(60)));
    PG_CONTAINER.start();
  }

  @Override
  public void after() {
    PG_CONTAINER.stop();
  }


  public PgConnection connect() throws SQLException {
    return (PgConnection) PG_CONTAINER.createConnection("");
  }
}
