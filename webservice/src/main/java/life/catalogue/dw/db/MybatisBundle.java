package life.catalogue.dw.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import life.catalogue.WsServerConfig;
import life.catalogue.db.MybatisFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class MybatisBundle implements ConfiguredBundle<WsServerConfig> {
  
  private static final Logger LOG = LoggerFactory.getLogger(MybatisBundle.class);
  private static final String NAME = "mybatis";
  
  private SqlSessionFactory sqlSessionFactory = null;
  private HikariDataSource dataSource;

  /**
   * Creates the bundle's MyBatis session factory and registers health checks.
   *
   * @param cfg         the application's configuration.
   * @param environment the Dropwizard environment being started.
   * @throws Exception if MyBatis setup fails for any reason. MyBatis exceptions will be
   *                   thrown as-is.
   */
  @Override
  public void run(WsServerConfig cfg, Environment environment) throws Exception {
    LOG.info("Connecting to database {} on {}", cfg.db.database, cfg.db.host);

    HikariConfig hik = cfg.db.hikariConfig();
    // pool healthchecks
    hik.setHealthCheckRegistry(environment.healthChecks());
    // expose pool metrics
    hik.setMetricRegistry(environment.metrics());
    // create datasource
    dataSource = new HikariDataSource(hik);
    
    // manage datasource
    ManagedHikariPool managedDs = new ManagedHikariPool(dataSource);
    environment.lifecycle().manage(managedDs);
    
    // create mybatis sqlsessionfactory
    sqlSessionFactory = MybatisFactory.configure(dataSource, environment.getName());
    
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
   * Returns a connection from the hikari pool.
   * Make sure to close it to return it to the pool.
   */
  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }
  
  /**
   * Initializes the bundle by doing nothing.
   *
   * @param bootstrap the Dropwizard bootstrap configuration.
   */
  @Override
  public void initialize(Bootstrap<?> bootstrap) {
  }
  
  protected String getName() {
    return NAME;
  }
}