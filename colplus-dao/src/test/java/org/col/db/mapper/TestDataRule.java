package org.col.db.mapper;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableSet;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.db.PgConfig;
import org.col.db.PgSetupRule;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A junit test rule that truncates all CoL tables, potentially loads some test
 * data from a sql dump file. Do not modify the db schema in the sql files.
 * <p>
 * The rule was designed to run as a junit {@link org.junit.Rule} before every
 * test.
 * <p>
 * Unless an explicit factory is given, this rule requires a connected postgres server with mybatis via the {@link PgSetupRule}.
 * Make sure its setup!
 */
public class TestDataRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(TestDataRule.class);
  public static final ColUser TEST_USER = new ColUser();
  
  static {
    TEST_USER.setUsername("test");
    TEST_USER.setFirstname("Tim");
    TEST_USER.setLastname("Tester");
    TEST_USER.setEmail("tim.test@mailinator.com");
    TEST_USER.getRoles().add(ColUser.Role.ADMIN);
  }
  
  final private TestData testData;
  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;
  
  public enum TestData {
    NONE(3),
    
    // apple datasetKey=11
    APPLE(3, 11, 12),
  
    // tree datasetKey=11
    TREE(3, 11),
    
    /**
     * Inits the datasets table with real col data from colplus-repo
     */
    DATASETS(2, 3);
    
    final Set<Integer> datasetKeys;
    
    TestData(Integer... datasetKeys) {
      if (datasetKeys == null) {
        this.datasetKeys = Collections.EMPTY_SET;
      } else {
        this.datasetKeys = ImmutableSet.copyOf(datasetKeys);
      }
    }
  }
  
  public static TestDataRule empty() {
    return new TestDataRule(TestData.NONE);
  }
  
  public static TestDataRule empty(SqlSessionFactory sqlSessionFactory) {
    return new TestDataRule(TestData.NONE, () -> sqlSessionFactory);
  }
  
  public static TestDataRule apple() {
    return new TestDataRule(TestData.APPLE);
  }
  
  public static TestDataRule apple(SqlSessionFactory sqlSessionFactory) {
    return new TestDataRule(TestData.APPLE, () -> sqlSessionFactory);
  }
  
  public static TestDataRule tree() {
    return new TestDataRule(TestData.TREE);
  }
  
  public static TestDataRule tree(SqlSessionFactory sqlSessionFactory) {
    return new TestDataRule(TestData.TREE, () -> sqlSessionFactory);
  }

  public static TestDataRule datasets() {
    return new TestDataRule(TestData.DATASETS);
  }
  
  public static TestDataRule datasets(SqlSessionFactory sqlSessionFactory) {
    return new TestDataRule(TestData.DATASETS, () -> sqlSessionFactory);
  }
  
  private TestDataRule(TestData testData, Supplier<SqlSessionFactory> sqlSessionFactorySupplier) {
    this.testData = testData;
    this.sqlSessionFactorySupplier = sqlSessionFactorySupplier;
  }
  
  public TestDataRule(TestData testData) {
    this(testData, () -> PgSetupRule.getSqlSessionFactory());
  }
  
  public <T> T getMapper(Class<T> mapperClazz) {
    return session.getMapper(mapperClazz);
  }
  
  public void commit() {
    session.commit();
  }
  
  public SqlSession getSqlSession() {
    return session;
  }
  
  @Override
  protected void before() throws Throwable {
    super.before();
    LOG.info("Loading {} test data", testData);
    session = sqlSessionFactorySupplier.get().openSession(false);
    // create required partitions to load data
    partition();
    truncate();
    loadData();
    // finally create a test user to use in tests
    session.getMapper(UserMapper.class).create(TEST_USER);
  }
  
  @Override
  protected void after() {
    super.after();
    session.close();
  }
  
  private void partition() {
    for (Integer dk : testData.datasetKeys) {
      PgSetupRule.partition(dk);
    }
  }
  
  private void truncate() throws SQLException {
    System.out.println("Truncate tables");
    java.sql.Statement st = session.getConnection().createStatement();
    st.execute("TRUNCATE coluser CASCADE");
    st.execute("TRUNCATE dataset CASCADE");
    session.getConnection().commit();
    st.close();
  }
  
  private void loadData() throws SQLException, IOException {
    ScriptRunner runner = new ScriptRunner(session.getConnection());
    runner.setSendFullScript(true);
    
    // common data for all tests and even the empty one
    runner.runScript(Resources.getResourceAsReader(PgConfig.DATA_FILE));
    session.getConnection().commit();
    
    if (testData != TestData.NONE) {
      System.out.format("Load %s test data\n\n", testData);
      switch (testData) {
        case DATASETS:
          // known datasets
          runner.runScript(Resources.getResourceAsReader(PgConfig.DATASETS_FILE));
          break;
        default:
          runner.runScript(Resources.getResourceAsReader(testData.name().toLowerCase() + ".sql"));
      }
    }
    session.getConnection().commit();
  }
  
}
