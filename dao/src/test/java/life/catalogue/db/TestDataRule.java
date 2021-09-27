package life.catalogue.db;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.postgres.PgCopyUtils;

import org.gbif.nameparser.api.NameType;

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

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.rules.ExternalResource;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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

  public final TestData testData;
  private SqlSession session;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;

  public final static TestData NONE = new TestData("none", null, null, null, true, false, Collections.emptyMap(),3);
  /**
   * Inits the datasets table with real col data from colplus-repo
   * The dataset.csv file was generated as a dump from production with psql:
   *
   * \copy (SELECT key,type,gbif_key,gbif_publisher_key,license,issued,confidence,completeness,origin,title,alias,description,version,geographic_scope,taxonomic_scope,url,logo,notes,settings,source_key,contact,creator,editor,publisher,contributor FROM dataset WHERE not private and deleted is null and origin = 'EXTERNAL' ORDER BY key) to 'dataset.csv' WITH CSV HEADER NULL '' ENCODING 'UTF8'
   */
  public final static TestData DATASETS = new TestData("datasets", null, null, null, false, true, Collections.emptyMap());
  public final static TestData DATASET_MIX = new TestData("dataset_mix", null, null, null, false, false, Collections.emptyMap());
  public final static TestData APPLE = new TestData("apple", 11, 3, 2, 3, 11, 12);
  public final static TestData FISH = new TestData("fish", 100, 2, 4, 3, 100, 101, 102);
  public final static TestData TREE = new TestData("tree", 11, 2, 2, 3, 11);
  public final static TestData TREE2 = new TestData("tree2", 11, 2, 2, 3, 11);
  public final static TestData DRAFT = new TestData("draft", 3, 1, 2, 3);
  public final static TestData DRAFT_WITH_SECTORS = new TestData("draft_with_sectors", 3, 2, 3, 3);

  public static class TestData {
    public final String name;
    public final Integer key;
    public final Set<Integer> datasetKeys;
    final Integer sciNameColumn;
    final Integer taxStatusColumn;
    final Map<String, Map<String, Object>> defaultValues;
    private final boolean datasets;
    private final boolean none;

    public TestData(String name, Integer key, Integer sciNameColumn, Integer taxStatusColumn, Integer... datasetKeys) {
      this(name, key, sciNameColumn, taxStatusColumn, Collections.emptyMap(), datasetKeys);
    }

    public TestData(String name, Integer key, Integer sciNameColumn, Integer taxStatusColumn, Map<String, Map<String, Object>> defaultValues, Integer... datasetKeys) {
      this(name, key, sciNameColumn, taxStatusColumn, false, false, defaultValues, datasetKeys);
    }

    private TestData(String name, Integer key, Integer sciNameColumn, Integer taxStatusColumn, boolean none, boolean initAllDatasets, Map<String, Map<String, Object>> defaultValues, Integer... datasetKeys) {
      this.name = name;
      this.key = key;
      this.sciNameColumn = sciNameColumn;
      this.taxStatusColumn = taxStatusColumn;
      if (datasetKeys == null) {
        this.datasetKeys = Collections.EMPTY_SET;
      } else {
        this.datasetKeys = ImmutableSet.copyOf(datasetKeys);
      }
      this.none = none;
      this.datasets = initAllDatasets;
      this.defaultValues = defaultValues;
    }

    @Override
    public String toString() {
      return name + " ("+ key +")";
    }
  }

  public static TestDataRule empty() {
    return new TestDataRule(NONE);
  }

  public static TestDataRule apple() {
    return new TestDataRule(APPLE);
  }

  public static TestDataRule apple(SqlSessionFactory sqlSessionFactory) {
    return new TestDataRule(APPLE, () -> sqlSessionFactory);
  }

  public static TestDataRule fish() {
    return new TestDataRule(FISH);
  }

  public static TestDataRule tree() {
    return new TestDataRule(TREE);
  }

  public static TestDataRule tree2() {
    return new TestDataRule(TREE2);
  }

  public static TestDataRule draft() {
    return new TestDataRule(DRAFT);
  }

  public static TestDataRule draftWithSectors() {
    return new TestDataRule(DRAFT_WITH_SECTORS);
  }

  public static TestDataRule datasets() {
    return new TestDataRule(DATASETS);
  }

  public static TestDataRule datasetMix() {
    return new TestDataRule(DATASET_MIX);
  }

  public static TestDataRule datasets(SqlSessionFactory sqlSessionFactory) {
    return new TestDataRule(DATASETS, () -> sqlSessionFactory);
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
    initSession();
    // remove potential old (global) data
    truncate(session);
    // populate dataset table with origins before we partition
    loadGlobalData();
    // create required partitions to load data
    partition();
    loadData();
    updateSequences();
    // finally create a test user to use in tests
    session.getMapper(UserMapper.class).create(TEST_USER);
    session.commit();
  }

  @Override
  protected void after() {
    session.close();

    try (SqlSession session = sqlSessionFactorySupplier.get().openSession(false)) {
      LOG.info("remove managed sequences not bound to a table");
      DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
      for (Dataset d : session.getMapper(DatasetMapper.class).process(null)) {
        LOG.debug("Remove managed sequences for dataset {}", d.getKey());
        pm.deleteManagedSequences(d.getKey());
      }
      session.commit();
    }
  }

  @Override
  public void close() {
    after();
  }

  public void initSession() {
    if (session == null) {
      var factory = sqlSessionFactorySupplier.get();
      DatasetInfoCache.CACHE.setFactory(factory);
      session = factory.openSession(false);
    }
  }

  public void partition() {
    for (Integer dk : testData.datasetKeys) {
      MybatisTestUtils.partition(session, dk);
    }
    session.commit();
  }

  public void updateSequences() throws Exception {
    DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    for (int dk : testData.datasetKeys) {
      pm.createManagedSequences(dk);
    }
    if (testData.key != null) {
      pm.updateIdSequences(testData.key);
    }
    session.commit();
  }

  private void truncate(SqlSession session) throws SQLException {
    LOG.info("Truncate global tables");
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      st.execute("TRUNCATE \"user\" CASCADE");
      session.getConnection().commit();
      st.execute("TRUNCATE dataset CASCADE");
      session.getConnection().commit();
      st.execute("TRUNCATE dataset_archive CASCADE");
      st.execute("TRUNCATE sector CASCADE");
      st.execute("TRUNCATE estimate CASCADE");
      st.execute("TRUNCATE decision CASCADE");
      st.execute("TRUNCATE name_match");
      st.execute("TRUNCATE names_index RESTART IDENTITY CASCADE");
      session.getConnection().commit();
    }
  }

  public void truncateDraft() throws SQLException {
    LOG.info("Truncate draft partition tables");
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      st.execute("TRUNCATE sector CASCADE");
      for (String table : new String[]{"name", "name_usage"}) {
        st.execute("TRUNCATE " + table + "_" + Datasets.COL + " CASCADE");
      }
      session.getConnection().commit();
    }
  }

  /**
   * Loads all data but the dataset table which is expected to been loaded before via loadDataset()
   */
  public void loadData() throws SQLException, IOException {
    session.getConnection().commit();

    ScriptRunner runner = new ScriptRunner(session.getConnection());
    runner.setSendFullScript(true);

    if (!testData.none) {
      System.out.format("Load %s test data\n\n", testData);

      try (Connection c = sqlSessionFactorySupplier.get().openSession(false).getConnection()) {
        PgConnection pgc = InitDbUtils.toPgConnection(c);
        if (!testData.datasets) {
            for (int key : testData.datasetKeys) {
              copyDataset(pgc, key);
              c.commit();
            }
            runner.runScript(Resources.getResourceAsReader("test-data/sequences.sql"));
          }
      }
    }
    session.commit();
  }

  /**
   * Loads only global data including the dataset table.
   * This is important to happen before other data is loaded,
   * as the dataset table defines the immutable origin that defines how partitions are layed out!
   */
  public void loadGlobalData() throws SQLException, IOException {
    try (Connection c = sqlSessionFactorySupplier.get().openSession(false).getConnection()) {
      PgConnection pgc = InitDbUtils.toPgConnection(c);

      // common data for all tests and even the empty one
      ScriptRunner runner = new ScriptRunner(session.getConnection());
      runner.setSendFullScript(true);
      runner.runScript(Resources.getResourceAsReader(InitDbUtils.DATA_FILE));

      if (testData.datasets) {
        // register known datasets
        InitDbUtils.insertDatasets(pgc);
      } else {
        copyGlobalTable(pgc, "dataset");
      }
      copyGlobalTable(pgc, "dataset_import");
      copyGlobalTable(pgc, "dataset_patch");
      copyGlobalTable(pgc, "dataset_archive");
      copyGlobalTable(pgc, "sector");
      if (copyGlobalTable(pgc, "names_index")) {
        // update names index keys if we added data
        session.getMapper(NamesIndexMapper.class).updateSequence();
      }
      copyGlobalTable(pgc, "name_match");

      c.commit();
    }
  }

  private void copyDataset(PgConnection pgc, int key) throws IOException, SQLException {
    copyPartitionedTable(pgc, "verbatim", key, ImmutableMap.of("dataset_key", key));
    copyPartitionedTable(pgc, "reference", key, datasetEntityDefaults(key));
    copyPartitionedTable(pgc, "name", key,
      datasetEntityDefaults(key, ImmutableMap.<String, Object>of(
        "origin", Origin.SOURCE,
        "type", NameType.SCIENTIFIC
      )),
      ImmutableMap.<String, Function<String[], String>>of(
        "scientific_name_normalized", row -> SciNameNormalizer.normalize(row[testData.sciNameColumn])
      )
    );
    copyPartitionedTable(pgc, "name_rel", key, datasetEntityDefaults(key));
    copyPartitionedTable(pgc, "name_usage", key,
      datasetEntityDefaults(key, ImmutableMap.<String, Object>of("origin", Origin.SOURCE)),
      ImmutableMap.<String, Function<String[], String>>of(
        "is_synonym", this::isSynonym
      )
    );
    copyPartitionedTable(pgc, "distribution", key, datasetEntityDefaults(key));
    copyPartitionedTable(pgc, "vernacular_name", key, datasetEntityDefaults(key));
  }

  private Map<String, Object> datasetEntityDefaults(int datasetKey) {
    return datasetEntityDefaults(datasetKey, new HashMap<>());
  }

  private Map<String, Object> datasetEntityDefaults(int datasetKey, Map<String, Object> defaults) {
    return ImmutableMap.<String, Object>builder()
        .putAll(defaults)
        .put("dataset_key", datasetKey)
        .put("created_by", 0)
        .put("modified_by", 0)
        .build();
  }

  private String isSynonym(String[] row) {
    TaxonomicStatus ts = TaxonomicStatus.valueOf(row[testData.taxStatusColumn]);
    return String.valueOf(ts.isSynonym());
  }

  private boolean copyGlobalTable(PgConnection pgc, String table) throws IOException, SQLException {
    return copyTable(pgc, table + ".csv", table, new HashMap<>(), Collections.EMPTY_MAP);
  }

  private boolean copyPartitionedTable(PgConnection pgc, String table, int datasetKey, Map<String, Object> defaults) throws IOException, SQLException {
    return copyPartitionedTable(pgc, table, datasetKey, defaults, new HashMap<>());
  }

  private boolean copyPartitionedTable(PgConnection pgc, String table, int datasetKey, Map<String, Object> defaults, Map<String, Function<String[], String>> funcs) throws IOException, SQLException {
    return copyTable(pgc, table + "_" + datasetKey + ".csv", table, defaults, funcs);
  }

  private boolean copyTable(PgConnection pgc, String filename, String table, Map<String, Object> defaults, Map<String, Function<String[], String>> funcs)
      throws IOException, SQLException {
    String resource = "/test-data/" + testData.name.toLowerCase() + "/" + filename;
    URL url = PgCopyUtils.class.getResource(resource);
    if (url != null) {
      // global defaults to add?
      if (testData.defaultValues.containsKey(table)) {
        defaults = new HashMap<>(defaults);
        defaults.putAll(testData.defaultValues.get(table));
      }
      PgCopyUtils.copy(pgc, table, resource, defaults, funcs);
      return true;
    }
    return false;
  }
}
