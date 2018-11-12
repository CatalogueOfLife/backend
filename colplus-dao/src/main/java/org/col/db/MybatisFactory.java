package org.col.db;

import javax.sql.DataSource;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.EnumOrdinalTypeHandler;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.col.api.model.Name;
import org.col.api.search.NameUsageWrapper;
import org.col.db.mapper.NameMapper;
import org.col.db.type.RankTypeHandler;
import org.col.db.type.UuidTypeHandler;
import org.col.db.type2.IntCount;
import org.col.db.type2.StringCount;
import org.gbif.nameparser.api.ParsedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures mybatis and provides a SqlSessionFactory for a given datasource.
 */
public class MybatisFactory {
  
  private static final Logger LOG = LoggerFactory.getLogger(MybatisFactory.class);
  
  /**
   * Configures an existing datasource with type aliases, handlers and mappers for
   * a mybatis sessionfactory. This can be used in test environments or proper
   * dropwizard applications.
   *
   * @param dataSource
   * @param environmentName
   */
  public static SqlSessionFactory configure(DataSource dataSource, String environmentName) {
    LOG.debug("Configure MyBatis");
    TransactionFactory transactionFactory = new JdbcTransactionFactory();
    org.apache.ibatis.mapping.Environment mybatisEnv = new org.apache.ibatis.mapping.Environment(
        environmentName, transactionFactory, dataSource);
    
    Configuration mybatisCfg = new Configuration(mybatisEnv);
    mybatisCfg.setMapUnderscoreToCamelCase(true);
    mybatisCfg.setLazyLoadingEnabled(false);
    mybatisCfg.setCacheEnabled(false);
    // mybatisCfg.setLocalCacheScope(LocalCacheScope.STATEMENT);
    // mybatisCfg.setDefaultExecutorType(ExecutorType.SIMPLE);
    
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
    // register all aliases from the api packages
    registry.registerAliases(Name.class.getPackage().getName());
    registry.registerAliases(ParsedName.class.getPackage().getName());
    registry.registerAliases(NameUsageWrapper.class.getPackage().getName());
    registry.registerAlias(IntCount.class);
    registry.registerAlias(StringCount.class);
  }
  
  private static void registerTypeHandlers(TypeHandlerRegistry registry) {
    // register all type handler from the type subpackage
    registry.register(UuidTypeHandler.class.getPackage().getName());
    registry.setDefaultEnumTypeHandler(EnumOrdinalTypeHandler.class);
    registry.register(RankTypeHandler.class);
    
  }
  
}