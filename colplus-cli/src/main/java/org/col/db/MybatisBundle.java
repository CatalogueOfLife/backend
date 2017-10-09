package org.col.db;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.commands.config.CliConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MybatisBundle implements ConfiguredBundle<CliConfig> {
	
	private static final Logger LOG = LoggerFactory.getLogger(MybatisBundle.class);
	private static final String NAME = "mybatis";

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
	public void run(CliConfig cfg, Environment environment) throws Exception {
		// create datasource
		HikariDataSource ds = cfg.db.pool();
		// create mybatis sqlsessionfactory
    SqlSessionFactory sqlSessionFactory = MybatisFactory.configure(ds, environment.getName());

		// manage datasource
		ManagedHikariPool managedDs = new ManagedHikariPool(ds);
		environment.lifecycle().manage(managedDs);
		// expose pool metrics
		ds.setMetricRegistry(environment.metrics());
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