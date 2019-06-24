package org.col.db.mapper;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.model.ColUser;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;
import org.col.common.tax.SciNameNormalizer;
import org.col.db.PgConfig;
import org.col.db.PgSetupRule;
import org.col.postgres.PgCopyUtils;
import org.gbif.nameparser.api.NameType;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
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
    NONE(null, null, null,2,3),
    
    // apple datasetKey=11
    APPLE(11, 3,2, 2,3,11,12),
  
    // tree datasetKey=11
    TREE(11, 2, 2, 2,3,11),
  
    // basic draft hierarchy
    DRAFT(3, 1, 2,2,3),
  
    // basic draft hierarchy
    DRAFT_WITH_SECTORS(3, 2, 3,2,3),

    /**
     * Inits the datasets table with real col data from colplus-repo
     */
    DATASETS(2, 3, null);
  
    public final Integer key;
    public final Integer sciNameColumn;
    public final Integer taxStatusColumn;
    final Set<Integer> datasetKeys;
    
    TestData(Integer key, Integer sciNameColumn, Integer taxStatusColumn, Integer... datasetKeys) {
      this.key = key;
      this.sciNameColumn = sciNameColumn;
      this.taxStatusColumn = taxStatusColumn;
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
  
  public static TestDataRule draft() {
    return new TestDataRule(TestData.DRAFT);
  }
  
  public static TestDataRule draftWithSectors() {
    return new TestDataRule(TestData.DRAFT_WITH_SECTORS);
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
    this(testData, PgSetupRule::getSqlSessionFactory);
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
    LOG.info("Loading {} test data", testData);
    super.before();
    session = sqlSessionFactorySupplier.get().openSession(false);
    truncate();
    // create required partitions to load data
    partition();
    loadData(false);
    // finally create a test user to use in tests
    session.getMapper(UserMapper.class).create(TEST_USER);
    session.commit();
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
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      st.execute("TRUNCATE coluser CASCADE");
      st.execute("TRUNCATE dataset CASCADE");
      st.execute("TRUNCATE sector CASCADE");
      st.execute("TRUNCATE estimate CASCADE");
      st.execute("TRUNCATE decision  CASCADE");
      session.getConnection().commit();
    }
  }
  
  public void truncateDraft() throws SQLException {
    System.out.println("Truncate draft partition tables");
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      st.execute("TRUNCATE sector CASCADE");
      for (String table : new String[]{"name", "name_usage"}) {
        st.execute("TRUNCATE " + table + "_"+ Datasets.DRAFT_COL +" CASCADE");
      }
      session.getConnection().commit();
    }
  }
  
  /**
   * @param skipGlobalTable if true only loads tables partitioned by datasetKey
   */
  public void loadData(final boolean skipGlobalTable) throws SQLException, IOException {
    session.getConnection().commit();
  
    ScriptRunner runner = new ScriptRunner(session.getConnection());
    runner.setSendFullScript(true);
  
    if (!skipGlobalTable){
      // common data for all tests and even the empty one
      runner.runScript(Resources.getResourceAsReader(PgConfig.DATA_FILE));
    }
  
    if (testData != TestData.NONE) {
      System.out.format("Load %s test data\n\n", testData);
      
      
      if (testData == TestData.DATASETS) {
        // known datasets
        runner.runScript(Resources.getResourceAsReader(PgConfig.DATASETS_FILE));
  
      } else {
        try (Connection c = sqlSessionFactorySupplier.get().openSession(false).getConnection()) {
          String resData = "/test-data/" + testData.name().toLowerCase();
          PgConnection pgc;
          if (c instanceof HikariProxyConnection) {
            HikariProxyConnection hpc = (HikariProxyConnection) c;
            pgc = hpc.unwrap(PgConnection.class);
          } else {
            pgc = (PgConnection) c;
          }
  
          if (!skipGlobalTable){
            String dRes = resData + "/dataset.csv";
            URL url = PgCopyUtils.class.getResource(dRes);
            if (url != null) {
              PgCopyUtils.copy(pgc, "dataset", dRes);
            }
          }
          copyTable(pgc, "verbatim");
          copyTable(pgc, "reference",
              ImmutableMap.<String, Object>of(
                  "created_by", 0,
                  "modified_by", 0
              )
          );
          copyTable(pgc, "name",
              ImmutableMap.<String, Object>of(
              "created_by", 0,
              "modified_by", 0,
              "origin", Origin.SOURCE,
              "type", NameType.SCIENTIFIC
              ),
              ImmutableMap.<String, Function<String[], String>>of(
                  "scientific_name_normalized", row -> SciNameNormalizer.normalize(row[testData.sciNameColumn])
              )
          );
          copyTable(pgc, "name_rel",
              ImmutableMap.<String, Object>of(
                  "created_by", 0,
                  "modified_by", 0
              )
          );
          copyTable(pgc, "name_usage",
              ImmutableMap.<String, Object>of(
                  "created_by", 0,
                  "modified_by", 0,
                  "origin", Origin.SOURCE
              ),
              ImmutableMap.<String, Function<String[], String>>of(
                  "is_synonym", this::isSynonym
              )
          );
          copyTable(pgc, "distribution",
              ImmutableMap.<String, Object>of(
                  "created_by", 0,
                  "modified_by", 0
              )
          );
          copyTable(pgc, "vernacular_name",
              ImmutableMap.<String, Object>of(
                  "created_by", 0,
                  "modified_by", 0
              )
          );
    
          c.commit();
  
          runner.runScript(Resources.getResourceAsReader("test-data/sequences.sql"));
        }
        
        session.commit();
      }
    }
  }
  
  private String isSynonym(String[] row) {
    int statusKey = Integer.valueOf(row[testData.taxStatusColumn]);
    TaxonomicStatus ts = TaxonomicStatus.values()[statusKey];
    return String.valueOf(ts.isSynonym());
  }
  
  private void copyTable(PgConnection pgc, String table) throws IOException, SQLException {
    copyTable(pgc, table, Collections.EMPTY_MAP, Collections.EMPTY_MAP);
  }
  
  private void copyTable(PgConnection pgc, String table, Map<String, Object> defaults) throws IOException, SQLException {
    copyTable(pgc, table, defaults, Collections.EMPTY_MAP);
  }

  private void copyTable(PgConnection pgc, String table, Map<String, Object> defaults, Map<String, Function<String[], String>> funcs)
      throws IOException, SQLException {
    String resource = "/test-data/" + testData.name().toLowerCase() + "/" + table + ".csv";
    URL url = PgCopyUtils.class.getResource(resource);
    if (url != null) {
      PgCopyUtils.copy(pgc, table + "_" + testData.key, resource,
          ImmutableMap.<String, Object>builder()
              .putAll(defaults)
              .put("dataset_key", testData.key)
              .build(),
          funcs
      );
    }
  }
}
