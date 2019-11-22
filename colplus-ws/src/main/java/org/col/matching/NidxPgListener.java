package org.col.matching;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import com.google.common.base.Strings;
import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;
import org.col.db.PgConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NidxPgListener implements PGNotificationListener, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(NidxPgListener.class);
  
  private final NameIndex ni;
  private final PgConfig cfg;
  private PGConnection con;
  
  public NidxPgListener(PgConfig cfg, NameIndex ni) {
    this.cfg = cfg;
    this.ni = ni;
    try {
      listen();
    } catch (SQLException e) {
      LOG.warn("Failed to connect to postgres", e);
    }
  }
  
  private void listen() throws SQLException {
    LOG.info("connecting to postgres {}/{}", cfg.host, cfg.database);
    con = DriverManager.getConnection(
        cfg.jdbcNgUrl(),
        Strings.emptyToNull(cfg.user),
        Strings.emptyToNull(cfg.password))
        .unwrap(PGConnection.class);
    con.addNotificationListener(this);
    Statement stmt = con.createStatement();
    stmt.executeUpdate("LISTEN nidx");
    stmt.close();
  }
  
  @Override
  public void notification(int processId, String channelName, String payload) {
    LOG.info("Received Notification: {}, {}, {}", processId, channelName, payload);
  }
  
  @Override
  public void closed() {
    // initiate reconnection & restart listening
    LOG.warn("Connection closed, try to reconnect to postgres");
    try {
      listen();
    } catch (SQLException e) {
      LOG.error("Failed to reconnect to postgres {}/{}", cfg.host,  cfg.database, e);
      //TODO: retry
    }
  }
  
  public void close() throws Exception {
    if (con != null) {
      con.close();
    }
  }
}
