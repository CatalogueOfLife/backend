package org.col.db;

import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.EnumOrdinalTypeHandler;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.col.api.Name;
import org.col.config.ColAppConfig;
import org.col.db.mapper.NameMapper;
import org.col.db.type.RankTypeHandler;
import org.col.db.type.UuidTypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public class MybatisBundle implements ConfiguredBundle<ColAppConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(MybatisBundle.class);
  private static final String NAME = "mybatis";

  private SqlSessionFactory sqlSessionFactory = null;


  /**
   * Creates the bundle's MyBatis session factory and registers health checks.
   *
   * @param cfg         the application's configuration.
   * @param environment the Dropwizard environment being started.
   * @throws Exception if MyBatis setup fails for any reason. MyBatis exceptions will be thrown as-is.
   */
  @Override
  public void run(ColAppConfig cfg, Environment environment) throws Exception {
    // create datasource
    HikariDataSource ds = cfg.db.pool();
    // create mybatis sqlsessionfactory
    sqlSessionFactory = configure(ds, environment.getName());

    // manage datasource
    ManagedHikariPool managedDs = new ManagedHikariPool(ds);
    environment.lifecycle().manage(managedDs);
    // expose pool metrics
    ds.setMetricRegistry(environment.metrics());
    // pool healthchecks
    ds.setHealthCheckRegistry(environment.healthChecks());
    environment.healthChecks().register("db-ping", new SqlSessionFactoryHealthCheck(sqlSessionFactory));

    // register sqlsession provider
    environment.jersey().register(SqlSessionProvider.binder(sqlSessionFactory));
  }

  /**
   * Configures an existing datasource with type aliases, handlers and mappers for a mybatis sessionfactory.
   * This can be used in test environments or proper dropwizard applications.
   *
   * @param dataSource
   * @param environmentName
   */
  public static SqlSessionFactory configure(DataSource dataSource, String environmentName) {
    LOG.debug("Configure MyBatis");
    TransactionFactory transactionFactory = new JdbcTransactionFactory();
    org.apache.ibatis.mapping.Environment mybatisEnv = new org.apache.ibatis.mapping.Environment(environmentName, transactionFactory, dataSource);

    Configuration mybatisCfg = new Configuration(mybatisEnv);
    mybatisCfg.setMapUnderscoreToCamelCase(true);
    mybatisCfg.setLazyLoadingEnabled(false);
    mybatisCfg.setCacheEnabled(false);
    //mybatisCfg.setLocalCacheScope(LocalCacheScope.STATEMENT);
    //mybatisCfg.setDefaultExecutorType(ExecutorType.SIMPLE);

    // aliases
    registerTypeAliases(mybatisCfg.getTypeAliasRegistry());

    // type handler
    registerTypeHandlers(mybatisCfg.getTypeHandlerRegistry());

    // mapper
    registerMapper(mybatisCfg.getMapperRegistry());

    SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
    return builder.build(mybatisCfg);
  }

  private static void registerMapper(MapperRegistry registry) {
    // register all mappers from the mapper subpackage
    registry.addMappers(NameMapper.class.getPackage().getName());
  }

  private static void registerTypeAliases(TypeAliasRegistry registry) {
    // register all aliases from the api package
    registry.registerAliases(Name.class.getPackage().getName());
  }


  private static void registerTypeHandlers(TypeHandlerRegistry registry) {
    // register all type handler from the type subpackage
    registry.register(UuidTypeHandler.class.getPackage().getName());
    registry.setDefaultEnumTypeHandler(EnumOrdinalTypeHandler.class);
    registry.register(RankTypeHandler.class);
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