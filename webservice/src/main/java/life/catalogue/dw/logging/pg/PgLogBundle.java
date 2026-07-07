package life.catalogue.dw.logging.pg;

import life.catalogue.WsServerConfig;
import life.catalogue.db.MybatisFactory;
import life.catalogue.dw.managed.ManagedUtils;

import org.apache.ibatis.session.SqlSessionFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.dropwizard.core.ConfiguredBundle;
import io.dropwizard.core.setup.Environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds request log collecting to the application, adding a request and response filter
 * that collect logs into memory and asynchronously persist these logs to the database from time to time.
 */
public class PgLogBundle implements ConfiguredBundle<WsServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(PgLogBundle.class);

  private PgLogCollector collector;
  // true when a dedicated writable db is configured for logs, so the shared app factory must not override it
  private boolean dedicatedFactory;

  @Override
  public void run(WsServerConfig cfg, Environment environment) {
    if (cfg.pgLog == null) {
      LOG.warn("PgLogging turned off, no configuration provided.");
    } else {
      collector = new PgLogCollector(cfg.pgLog);
      environment.lifecycle().manage(ManagedUtils.from(collector));

      // if a dedicated, writable db is configured persist logs there instead of the shared app connection.
      // this is required on the read-only server whose main connection points at a read-only standby.
      if (cfg.pgLog.db != null) {
        LOG.info("Persist api logs to dedicated database {}", cfg.pgLog.db.location());
        HikariConfig hik = cfg.pgLog.db.hikariConfig();
        hik.setPoolName("pglog");
        HikariDataSource ds = new HikariDataSource(hik);
        environment.lifecycle().manage(ManagedUtils.from(ds));
        collector.setFactory(MybatisFactory.configure(ds, "pglog"));
        dedicatedFactory = true;
      }

      // add filters
      environment.jersey().register(new PgLogRequestFilter());
      environment.jersey().register(new PgLogResponseFilter(collector));
    }
  }

  /**
   * Wires up the shared mybatis sqlfactory to be used, unless a dedicated writable db was configured.
   */
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    if (collector != null && !dedicatedFactory) {
      collector.setFactory(factory);
    }
  }

}
