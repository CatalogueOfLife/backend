package life.catalogue.dw.logging.pg;

import life.catalogue.WsServerConfig;

import life.catalogue.dw.managed.ManagedUtils;

import org.apache.ibatis.session.SqlSessionFactory;

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

  @Override
  public void run(WsServerConfig cfg, Environment environment) {
    if (cfg.pgLog == null) {
      LOG.warn("PgLogging turned off, no configuration provided.");
    } else {
      collector = new PgLogCollector(cfg.pgLog);
      environment.lifecycle().manage(ManagedUtils.from(collector));

      // add filters
      environment.jersey().register(new PgLogRequestFilter());
      environment.jersey().register(new PgLogResponseFilter(collector));
    }
  }

  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    if (collector != null) {
      collector.setFactory(factory);
    }
  }

}
