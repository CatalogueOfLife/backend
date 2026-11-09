package life.catalogue.event;

import life.catalogue.db.PgConfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventBrokers {
  private static final Logger LOG = LoggerFactory.getLogger(EventBrokers.class);

  private EventBrokers() {
  }

  /**
   * Builds the broker of a server.
   * A server reading from a standby listens and publishes on the primary configured as broker.db and waits for the
   * standby to replay a change before handling its event; any other publishes on its main database and listens there
   * unless broker.db says otherwise.
   *
   * @param mainDb the configuration of the server's main database
   * @param mainFactory the pooled connections to the server's main database
   */
  public static EventBroker build(BrokerConfig cfg, PgConfig mainDb, SqlSessionFactory mainFactory) {
    if (cfg.mode == BrokerConfig.Mode.LOCAL) {
      LOG.info("Use a local event broker within this JVM");
      return new LocalEventBroker();
    }
    final boolean standby = isStandby(mainFactory);
    if (standby && cfg.db == null) {
      throw new IllegalStateException("The main database " + mainDb.location() + " is a read only standby. Configure broker.db with the primary to listen on");
    }
    final PgConfig listenDb = cfg.db == null ? mainDb : cfg.db;
    LOG.info("Use a postgres event broker on {}{}", listenDb.location(), standby ? ", main database is a standby" : "");
    return new PgEventBroker(cfg, listenDb, standby ? null : mainFactory, standby ? mainFactory : null);
  }

  private static boolean isStandby(SqlSessionFactory factory) {
    try (SqlSession session = factory.openSession(true);
         Statement st = session.getConnection().createStatement();
         ResultSet rs = st.executeQuery("SELECT pg_is_in_recovery()")
    ) {
      rs.next();
      return rs.getBoolean(1);
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot tell whether the main database is a standby", e);
    }
  }
}
