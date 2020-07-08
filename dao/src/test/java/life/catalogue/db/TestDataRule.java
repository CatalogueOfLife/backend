package life.catalogue.db;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.postgres.PgCopyUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.NameType;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

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
public class TestDataRule extends ExternalResource implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(TestDataRule.class);
  public static final User TEST_USER = new User();

  static {
    TEST_USER.setUsername("test");
    TEST_USER.setFirstname("Tim");
    TEST_USER.setLastname("Tester");
    TEST_USER.setEmail("tim.test@mailinator.com");
    TEST_USER.getRoles().add(User.Role.ADMIN);
  }

  final private TestData testData;
  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;

  public enum TestData {
    NONE(null, null, null, 1, 3),

    // apple datasetKey=11
    APPLE(11, 3, 2, 1, 3, 11, 12),

    // tree datasetKey=11
    TREE(11, 2, 2, 1, 3, 11),

    // basic draft hierarchy
    DRAFT(3, 1, 2, 1, 3),

    // basic draft hierarchy
    DRAFT_WITH_SECTORS(3, 2, 3, 1, 3),

    /**
     * Inits the datasets table with real col data from colplus-repo
     */
    DATASETS(1, 3, null);

    public final Integer key;
    public final Integer sciNameColumn;
    public final Integer taxStatusColumn;
    public final Set<Integer> datasetKeys;

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
    initSession();
    truncate();
    // create required partitions to load data
    partition();
    loadData(false);
    updateSequences();
    // finally create a test user to use in tests
    session.getMapper(UserMapper.class).create(TEST_USER);
    session.commit();
  }

  @Override
  protected void after() {
    super.after();
    session.close();
  }

  @Override
  public void close() {
    after();
  }

  public void initSession() {
    if (session == null) {
      session = sqlSessionFactorySupplier.get().openSession(false);
    }
  }

  public void partition() {
    for (Integer dk : testData.datasetKeys) {
      PgSetupRule.partition(dk);
    }
  }

  public void updateSequences() throws Exception {
    if (testData.key != null) {
      try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
        DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
        pm.updateIdSequences(testData.key);
        // names index keys
        try (java.sql.Statement st = session.getConnection().createStatement()) {
          st.execute("ALTER SEQUENCE names_index_id_seq RESTART WITH 1");
        }
        session.commit();
      }
    }
  }

  private void truncate() throws SQLException {
    System.out.println("Truncate tables");
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      st.execute("TRUNCATE \"user\" CASCADE");
      st.execute("TRUNCATE dataset CASCADE");
      st.execute("TRUNCATE sector CASCADE");
      st.execute("TRUNCATE estimate CASCADE");
      st.execute("TRUNCATE decision CASCADE");
      st.execute("TRUNCATE names_index");
      session.getConnection().commit();
    }
  }

  public void truncateDraft() throws SQLException {
    System.out.println("Truncate draft partition tables");
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      st.execute("TRUNCATE sector CASCADE");
      for (String table : new String[]{"name", "name_usage"}) {
        st.execute("TRUNCATE " + table + "_" + Datasets.DRAFT_COL + " CASCADE");
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

    if (!skipGlobalTable) {
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

          if (!skipGlobalTable) {
            copyGlobalTable(pgc, "dataset");
            copyGlobalTable(pgc, "sector");
          }
          copyPartitionedTable(pgc, "verbatim", ImmutableMap.of("dataset_key", testData.key));
          copyPartitionedTable(pgc, "reference", datasetEntityDefaults());
          copyPartitionedTable(pgc, "name",
              datasetEntityDefaults(ImmutableMap.<String, Object>of(
                  "origin", Origin.SOURCE,
                  "type", NameType.SCIENTIFIC
              )),
              ImmutableMap.<String, Function<String[], String>>of(
                  "scientific_name_normalized", row -> SciNameNormalizer.normalize(row[testData.sciNameColumn])
              )
          );
          copyPartitionedTable(pgc, "name_rel", datasetEntityDefaults());
          copyPartitionedTable(pgc, "name_usage",
              datasetEntityDefaults(ImmutableMap.<String, Object>of("origin", Origin.SOURCE)),
              ImmutableMap.<String, Function<String[], String>>of(
                  "is_synonym", this::isSynonym
              )
          );
          copyPartitionedTable(pgc, "distribution", datasetEntityDefaults());
          copyPartitionedTable(pgc, "vernacular_name", datasetEntityDefaults());

          c.commit();

          runner.runScript(Resources.getResourceAsReader("test-data/sequences.sql"));
        }

        session.commit();
      }
    }
  }

  private Map<String, Object> datasetEntityDefaults() {
    return datasetEntityDefaults(new HashMap<>());
  }

  private Map<String, Object> datasetEntityDefaults(Map<String, Object> defaults) {
    return ImmutableMap.<String, Object>builder()
        .putAll(defaults)
        .put("dataset_key", testData.key)
        .put("created_by", 0)
        .put("modified_by", 0)
        .build();
  }

  private String isSynonym(String[] row) {
    TaxonomicStatus ts = TaxonomicStatus.valueOf(row[testData.taxStatusColumn]);
    return String.valueOf(ts.isSynonym());
  }

  private void copyGlobalTable(PgConnection pgc, String table) throws IOException, SQLException {
    copyTable(pgc, table + ".csv", table, Collections.EMPTY_MAP, Collections.EMPTY_MAP);
  }

  private void copyPartitionedTable(PgConnection pgc, String table) throws IOException, SQLException {
    copyPartitionedTable(pgc, table, Collections.EMPTY_MAP, Collections.EMPTY_MAP);
  }

  private void copyPartitionedTable(PgConnection pgc, String table, Map<String, Object> defaults) throws IOException, SQLException {
    copyPartitionedTable(pgc, table, defaults, Collections.EMPTY_MAP);
  }

  private void copyPartitionedTable(PgConnection pgc, String table, Map<String, Object> defaults, Map<String, Function<String[], String>> funcs) throws IOException, SQLException {
    copyTable(pgc, table + ".csv", table + "_" + testData.key, defaults, funcs);
  }

  private void copyTable(PgConnection pgc, String filename, String table, Map<String, Object> defaults, Map<String, Function<String[], String>> funcs)
      throws IOException, SQLException {
    String resource = "/test-data/" + testData.name().toLowerCase() + "/" + filename;
    URL url = PgCopyUtils.class.getResource(resource);
    if (url != null) {
      PgCopyUtils.copy(pgc, table, resource, defaults, funcs);
    }
  }
}
