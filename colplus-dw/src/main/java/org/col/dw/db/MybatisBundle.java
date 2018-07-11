package org.col.dw.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.db.MybatisFactory;
import org.col.dw.PgAppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MybatisBundle implements ConfiguredBundle<PgAppConfig> {

	private static final Logger LOG = LoggerFactory.getLogger(MybatisBundle.class);
	private static final String NAME = "mybatis";

	private SqlSessionFactory sqlSessionFactory = null;

	/**
	 * Creates the bundle's MyBatis session factory and registers health checks.
	 *
	 * @param cfg
	 *          the application's configuration.
	 * @param environment
	 *          the Dropwizard environment being started.
	 * @throws Exception
	 *           if MyBatis setup fails for any reason. MyBatis exceptions will be
	 *           thrown as-is.
	 */
	@Override
	public void run(PgAppConfig cfg, Environment environment) throws Exception {
		HikariConfig hik = cfg.db.hikariConfig();
		// pool healthchecks
		hik.setHealthCheckRegistry(environment.healthChecks());
		// expose pool metrics
		hik.setMetricRegistry(environment.metrics());
		// create datasource
		HikariDataSource ds = new HikariDataSource(hik);

		// manage datasource
		ManagedHikariPool managedDs = new ManagedHikariPool(ds);
		environment.lifecycle().manage(managedDs);

		// create mybatis sqlsessionfactory
		sqlSessionFactory = MybatisFactory.configure(ds, environment.getName());

		environment.healthChecks().register("db-ping",
		    new SqlSessionFactoryHealthCheck(sqlSessionFactory));

		// register sqlsession provider
		environment.jersey().register(SqlSessionProvider.binder(sqlSessionFactory));
	}

  /**
   * Make sure to call this after the bundle has been initialized, otherwise its null
   */
  public SqlSessionFactory getSqlSessionFactory() {
    return sqlSessionFactory;
  }

  /**
	 * Initializes the bundle by doing nothing.
	 *
	 * @param bootstrap
	 *          the Dropwizard bootstrap configuration.
	 */
	@Override
	public void initialize(Bootstrap<?> bootstrap) {
	}

	protected String getName() {
		return NAME;
	}
}