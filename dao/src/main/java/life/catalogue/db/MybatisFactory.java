package life.catalogue.db;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.TreeNode;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.db.mapper.ArchivedNameMapper;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.IdReportMapper;
import life.catalogue.db.mapper.UsageNameID;
import life.catalogue.db.mapper.legacy.model.LName;
import life.catalogue.db.type.UuidTypeHandler;
import life.catalogue.db.type2.StringCount;

import org.gbif.nameparser.api.ParsedName;
import org.gbif.nameparser.utils.Closer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.wrapper.BeanWrapper;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapper;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.EnumTypeHandler;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

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
    // Disable all caching. Local session scoped caching is on be default and causing OOM problems for our indexing
    // see https://programmer.help/blogs/step-by-step-learning-the-caching-features-of-mybatis.html
    mybatisCfg.setLocalCacheScope(LocalCacheScope.STATEMENT);
    // 2nd level cache
    mybatisCfg.setCacheEnabled(false);
    mybatisCfg.setDefaultExecutorType(ExecutorType.SIMPLE);
    mybatisCfg.setObjectWrapperFactory(new ApiObjectWrapperFactory());
    // aliases
    registerTypeAliases(mybatisCfg.getTypeAliasRegistry());
    
    // type handler
    registerTypeHandlers(mybatisCfg.getTypeHandlerRegistry());
    
    // mapper
    registerMapper(mybatisCfg.getMapperRegistry());
    
    SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
    return requireUtf8Encoding(builder.build(mybatisCfg));
  }

  /**
   * Classes that extend from Map, e.g. DatasetSettings, need a differrent Wrapper then the default MapWrapper.
   */
  public static class ApiObjectWrapperFactory extends DefaultObjectWrapperFactory {
    @Override
    public boolean hasWrapperFor(Object object) {
      if (object instanceof DatasetSettings) {
        return true;
      }
      return super.hasWrapperFor(object);
    }

    @Override
    public ObjectWrapper getWrapperFor(MetaObject metaObject, Object object) {
      if (object instanceof DatasetSettings) {
        return new BeanWrapper(metaObject, object);
      }
      return super.getWrapperFor(metaObject, object);
    }
  }

  private static SqlSessionFactory requireUtf8Encoding(SqlSessionFactory factory) {
    Connection c = null;
    Statement st = null;
    try (SqlSession session = factory.openSession()) {
      c = session.getConnection();
      st = c.createStatement();

      st.execute("SHOW SERVER_ENCODING");
      st.getResultSet().next();
      String cs = st.getResultSet().getString(1);
      Preconditions.checkArgument("UTF8".equalsIgnoreCase(cs), "Bad server encoding");

      st.execute("SHOW CLIENT_ENCODING");
      st.getResultSet().next();
      cs = st.getResultSet().getString(1);
      Preconditions.checkArgument("UTF8".equalsIgnoreCase(cs), "Bad client encoding");

    } catch (SQLException e) {
      LOG.error("Failed to setup mybatis session factory", e);

    } finally {
      Closer.closeQuitely(st, c);
    }
    return factory;
  }

  private static void registerMapper(MapperRegistry registry) {
    // register the common mapper with a shorter namespace to keep include statements short
    registry.addMapper(Common.class);
    // register all mappers from the mapper subpackage
    registry.addMappers(NameMapper.class.getPackage().getName());
  }
  
  private static void registerTypeAliases(TypeAliasRegistry registry) {
    // register all aliases from the api packages
    registry.registerAliases(Name.class.getPackage().getName());
    // name parser API
    registry.registerAliases(ParsedName.class.getPackage().getName());
    // search package
    registry.registerAliases(NameUsageWrapper.class.getPackage().getName());
    // legacy package
    registry.registerAliases(LName.class.getPackage().getName());
    registry.registerAlias(StringCount.class);
    registry.registerAlias(UsageNameID.class);
    registry.registerAlias("TreeNodeMybatis", TreeNode.TreeNodeMybatis.class);
    registry.registerAlias("UsageDecision", Duplicate.UsageDecision.class);
    registry.registerAlias("DuplicateMybatis", Duplicate.Mybatis.class);
    registry.registerAlias("NameWithNidx", NameMapper.NameWithNidx.class);
    registry.registerAlias("ArchivedSimpleNameWithNidx", ArchivedNameMapper.ArchivedSimpleNameWithNidx.class);
  }
  
  private static void registerTypeHandlers(TypeHandlerRegistry registry) {
    // register all type handler from the type subpackage
    registry.register(UuidTypeHandler.class.getPackage().getName());
    registry.setDefaultEnumTypeHandler(EnumTypeHandler.class);
  }
  
}