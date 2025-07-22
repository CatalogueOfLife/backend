package life.catalogue.junit;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Origin;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.common.text.CSVUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.NamesIndexMapper;
import life.catalogue.db.mapper.UserMapper;
import life.catalogue.parser.NameParser;
import life.catalogue.pgcopy.CsvFunction;
import life.catalogue.pgcopy.PgCopyUtils;

import life.catalogue.postgres.PgAuthorshipNormalizer;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;

import javax.annotation.Nullable;

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
import com.google.common.collect.Lists;

import it.unimi.dsi.fastutil.Pair;

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
  private boolean skipAfter = false;
  private final Supplier<SqlSessionFactory> sqlSessionFactorySupplier;

  /**
   * NONE does wipe all data so every test starts with an empty db.
   */
  public final static TestData EMPTY = new TestData("empty", null, true, Collections.emptyMap(), Set.of(3), false, null);
  /**
   * KEEP keeps existing data and does not wipe or create anything new. Can be used with class based data loading rules, e.g. TxtTreeDataRule
   */
  public final static TestData KEEP = new TestData("keep", null, true, Collections.emptyMap(), Set.of(3), false, null);
  /**
   * Inits the datasets table with real col data from colplus-repo
   * The dataset.csv file was generated as a dump from production with psql:
   *
   * \copy (SELECT key,type,gbif_key,gbif_publisher_key,license,issued,confidence,completeness,origin,title,alias,description,version,geographic_scope,taxonomic_scope,url,logo,notes,settings,source_key,contact,creator,editor,publisher,contributor FROM dataset WHERE not private and deleted is null and origin = 'EXTERNAL' ORDER BY key) to 'dataset.csv' WITH CSV HEADER NULL '' ENCODING 'UTF8'
   */
  public final static TestData DATASET_MIX = new TestData("dataset_mix", null, false, Collections.emptyMap(), null, false, null);
  public final static TestData APPLE = new TestData("apple", 11, Set.of(3, 11, 12));
  public final static TestData FISH = new TestData("fish", 100, Set.of(3, 100, 101, 102, 103));
  public final static TestData TREE = new TestData("tree", 11, Set.of(3, 11, 12));
  public final static TestData TREE2 = new TestData("tree2", 11, Set.of(3, 11));
  public final static TestData TREE3 = new TestData("tree3", 3, Set.of(3));
  public final static TestData DRAFT = new TestData("draft", 3, Set.of(3));
  public final static TestData DRAFT_WITH_SECTORS = new TestData("draft_with_sectors", 3, Set.of(3));
  public final static TestData DRAFT_NAME_UPD = new TestData("draft_name_upd", 3, Set.of(3, 100, 101));
  public final static TestData DUPLICATES = new TestData("duplicates", 1000, Set.of(3, 1000));
  public final static TestData NIDX = new TestData("nidx", null, Set.of(100, 101, 102));
  public final static TestData COL_SYNCED = new TestData("colsynced", 3, null);

  public static class TestData {
    public final String name;
    public final Integer key;
    public final Set<Integer> datasetKeys;
    final Integer sciNameColumn;
    final Integer authorNameColumn;
    final Integer rankColumn;
    final Integer codeColumn;
    final Integer taxStatusColumn;
    final Map<String, Map<String, Object>> defaultValues;
    private final boolean parseNames;
    private final boolean none;
    public final Map<Pair<DataFormat, Integer>, Integer> keyMap;

    public TestData(String name, Integer key, Set<Integer> datasetKeys) {
      this(name, key, Collections.emptyMap(), datasetKeys);
    }

    public TestData(String name, Integer key, Set<Integer> datasetKeys, boolean parseNames) {
      this(name, key, false, Collections.emptyMap(), datasetKeys, parseNames, null);
    }
    public TestData(String name, Integer key, Set<Integer> datasetKeys, boolean parseNames, Map<Pair<DataFormat, Integer>, Integer> keyMap) {
      this(name, key, false, Collections.emptyMap(), datasetKeys, parseNames, keyMap);
    }

    public TestData(String name, Integer key, Map<String, Map<String, Object>> defaultValues, Set<Integer> datasetKeys) {
      this(name, key, false, defaultValues, datasetKeys, true, null);
    }
    public TestData(String name, Integer key, Map<String, Map<String, Object>> defaultValues, Set<Integer> datasetKeys, boolean parseNames) {
      this(name, key, false, defaultValues, datasetKeys, parseNames, null);
    }

    private TestData(String name, Integer key, boolean none, Map<String, Map<String, Object>> defaultValues, Set<Integer> datasetKeys,
                     boolean parseNames, Map<Pair<DataFormat, Integer>, Integer> keyMap) {
      this.name = name;
      this.key = key;
      // detect important columns
      var probeKey = findProbeKey(key, datasetKeys, keyMap);
      var ncols = readNameColumns(name, probeKey);
      this.sciNameColumn    = ncols[0];
      this.authorNameColumn = ncols[1];
      this.rankColumn       = ncols[2];
      this.codeColumn       = ncols[3];
      var tcols = readNameUsageColumns(name, probeKey);
      this.taxStatusColumn  = tcols[0];
      this.none = none;
      this.defaultValues = defaultValues;
      if (datasetKeys == null) {
        this.datasetKeys = readDatasetKeys(name);
      } else {
        this.datasetKeys = ImmutableSet.copyOf(datasetKeys);
      }
      this.keyMap=keyMap == null ? null : Map.copyOf(keyMap);
      this.parseNames = parseNames;
    }

    private Integer findProbeKey(Integer key, Set<Integer> datasetKeys, Map<Pair<DataFormat, Integer>, Integer> keyMap) {
      Set<Integer> keys = new HashSet<>();
      keys.add(Datasets.COL);
      if (key != null) {
        keys.add(key);
      }
      if (datasetKeys != null) {
        keys.addAll(datasetKeys);
      }
      if (keyMap != null) {
        keys.addAll(keyMap.values());
      }
      for (var k : keys) {
        try (var res = nameFileResource(name, k)) {
          if (res != null) {
            LOG.info("Use dataset key {} to probe for test data csv columns", k);
            return k;
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return null;
    }
    private static Set<Integer> readDatasetKeys(String testDataName) {
      String resource = "test-data/" + testDataName.toLowerCase() + "/dataset.csv";
      var in = TestDataRule.class.getClassLoader().getResourceAsStream(resource);
      var keys = new HashSet<Integer>();
      keys.add(Datasets.COL); // always there!
      if (in != null) {
        // requires dataset key to be the first column!
        CSVUtils.parse(in,1).forEach(d -> keys.add(Integer.valueOf(d.get(0))));
      }
      return keys;
    }

    private static InputStream nameFileResource(String testDataName, Integer key) {
      String resource = "test-data/" + testDataName.toLowerCase() + "/name_"+key+".csv";
      return TestDataRule.class.getClassLoader().getResourceAsStream(resource);
    }

    /**
     * [0] = name column
     * [1] = author column
     * [2] = rank column
     * [3] = code column
     */
    private static Integer[] readNameColumns(String testDataName, @Nullable Integer key) {
      var cols = new Integer[4];
      if (key != null) {
        var in = nameFileResource(testDataName, key);
        if (in != null) {
          int idx = 0;
          for (var colName : PgCopyUtils.readCsvHeader(in)) {
            if (colName.equals("scientific_name")) {
              cols[0] = idx;
            } else if (colName.equals("authorship")) {
              cols[1] = idx;
            } else if (colName.equals("rank")) {
              cols[2] = idx;
            } else if (colName.equals("code")) {
              cols[3] = idx;
            }
            idx++;
          }
        }
      }
      return cols;
    }

    /**
     * [0] = status column
     */
    private static Integer[] readNameUsageColumns(String testDataName, Integer key) {
      var cols = new Integer[1];
      if (key != null) {
        String resource = "test-data/" + testDataName.toLowerCase() + "/name_usage_" + key + ".csv";
        var in = TestDataRule.class.getClassLoader().getResourceAsStream(resource);
        if (in != null) {
          int idx = 0;
          for (var colName : PgCopyUtils.readCsvHeader(in)) {
            if (colName.equals("status")) {
              cols[0] = idx;
            }
            idx++;
          }
        }
      }
      return cols;
    }

    @Override
    public String toString() {
      return name + " ("+ key +")";
    }
  }

  public static TestDataRule empty() {
    return new TestDataRule(EMPTY);
  }

  public static TestDataRule keep() {
    return new TestDataRule(KEEP);
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

  public static TestDataRule duplicates() {
    return new TestDataRule(DUPLICATES);
  }

  public static TestDataRule tree() {
    return new TestDataRule(TREE);
  }

  public static TestDataRule tree2() {
    return new TestDataRule(TREE2);
  }
  public static TestDataRule tree3() {
    return new TestDataRule(TREE3);
  }

  public static TestDataRule draft() {
    return new TestDataRule(DRAFT);
  }

  public static TestDataRule draftWithSectors() {
    return new TestDataRule(DRAFT_WITH_SECTORS);
  }

  public static TestDataRule draftNameUpd() {
    return new TestDataRule(DRAFT_NAME_UPD);
  }

  public static TestDataRule datasetMix() {
    return new TestDataRule(DATASET_MIX);
  }

  public static TestDataRule nidx() {
    return new TestDataRule(NIDX);
  }

  public static TestDataRule colSynced() {
    return new TestDataRule(COL_SYNCED);
  }

  private TestDataRule(TestData testData, Supplier<SqlSessionFactory> sqlSessionFactorySupplier) {
    this.testData = testData;
    this.sqlSessionFactorySupplier = sqlSessionFactorySupplier;
  }

  public TestDataRule(TestData testData) {
    this(testData, SqlSessionFactoryRule::getSqlSessionFactory);
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

  public int mapKey(DataFormat format, int sourceKey) {
    return testData.keyMap.get(Pair.of(format, sourceKey));
  }

  @Override
  protected void before() throws Throwable {
    initSession();
    LOG.info("Loading {} test data into {}", testData, session.getConnection().getMetaData().getURL());
    if (testData != KEEP) {
      // remove potential old data
      truncate(session);
      // populate dataset table with origins before we partition
      loadGlobalData();
      loadData();
      // populate global tables that refer to partitioned data
      loadGlobalData2();
      updateSequences();
      // finally create a test user to use in tests
      session.getMapper(UserMapper.class).create(TEST_USER);
    }
    session.commit();
  }

  @Override
  protected void after() {
    if (!skipAfter) {
      session.close();

      try (SqlSession session = sqlSessionFactorySupplier.get().openSession(false)) {
        LOG.info("remove sequences");
        DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
        for (Dataset d : session.getMapper(DatasetMapper.class).process(null)) {
          LOG.debug("Remove sequences for dataset {}", d.getKey());
          pm.deleteSequences(d.getKey());
        }
        session.commit();
      }
    }
  }

  @Override
  public void close() {
    after();
  }

  public void skipAfter() {
    this.skipAfter = true;
  }

  public void initSession() {
    if (session == null) {
      var factory = sqlSessionFactorySupplier.get();
      DatasetInfoCache.CACHE.setFactory(factory);
      session = factory.openSession(false);
    }
  }

  public void updateSequences() {
    DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    for (int dk : testData.datasetKeys) {
      pm.createSequences(dk);
      pm.updateSequences(dk);
    }
    session.commit();
  }

  private void truncate(SqlSession session) throws SQLException {
    LOG.info("Truncate data tables");
    DatasetInfoCache.CACHE.clear();
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      var dpm = session.getMapper(DatasetPartitionMapper.class);

      st.execute("TRUNCATE \"user\", dataset, name_usage, name, reference, verbatim_source_secondary, verbatim_source, verbatim CASCADE"); // this should cascade to all data partitions, but to make sure we also do:
      session.getConnection().commit();
      st.execute("TRUNCATE dataset_archive CASCADE");
      st.execute("TRUNCATE sector, estimate, decision CASCADE");
      st.execute("TRUNCATE name_match");
      st.execute("TRUNCATE names_index RESTART IDENTITY CASCADE");
      session.getConnection().commit();
    }
  }

  public void truncateDraft() throws SQLException {
    LOG.info("Truncate draft partition tables");
    PgUtils.killNoneIdleConnections(session);
    try (java.sql.Statement st = session.getConnection().createStatement()) {
      st.execute("DELETE FROM name_match WHERE dataset_key=" + Datasets.COL);
      session.getConnection().commit();
      for (String t : Lists.reverse(DatasetPartitionMapper.PARTITIONED_TABLES)){
        st.execute("DELETE FROM " + t + " WHERE dataset_key=" + Datasets.COL);
      };
      session.getConnection().commit();
      for (String t : new String[]{"sector_import", "sector"}){
        st.execute("DELETE FROM " + t + " WHERE dataset_key=" + Datasets.COL);
      };
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
    runner.setLogWriter(null); // dont print the SQL to stdout

    if (!testData.none) {
      System.out.format("Load %s test data\n\n", testData);

      try (Connection c = sqlSessionFactorySupplier.get().openSession(false).getConnection()) {
        PgConnection pgc = InitDbUtils.toPgConnection(c);
        for (int key : testData.datasetKeys) {
          copyDataset(pgc, key);
          c.commit();
        }
        runner.runScript(Resources.getResourceAsReader("test-data/sequences.sql"));
      }
    }
    session.commit();
  }

  /**
   * Loads most global data including the dataset table, but not the name_match tables which requires name data to exist.
   * This is important to happen before other data is loaded,
   * as the dataset table defines the immutable origin that defines how partitions are layed out!
   */
  public void loadGlobalData() throws SQLException, IOException {
    try (Connection c = sqlSessionFactorySupplier.get().openSession(false).getConnection()) {
      PgConnection pgc = InitDbUtils.toPgConnection(c);

      // common data for all tests and even the empty one
      ScriptRunner runner = new ScriptRunner(session.getConnection());
      runner.setSendFullScript(true);
      runner.setStopOnError(true);
      runner.setLogWriter(null); // don't write to stdout
      runner.runScript(Resources.getResourceAsReader(InitDbUtils.DATA_FILE));

      copyGlobalTable(pgc, "dataset");
      copyGlobalTable(pgc, "dataset_import");
      copyGlobalTable(pgc, "dataset_patch");
      copyGlobalTable(pgc, "dataset_archive");
      copyGlobalTable(pgc, "sector");
      if (copyGlobalTable(pgc, "names_index")) {
        // update names index keys if we added data
        session.getMapper(NamesIndexMapper.class).updateSequence();
      }
      copyGlobalTable(pgc, "name_usage_archive", ImmutableMap.of(
        "origin", Origin.SOURCE,
          "n_origin", Origin.SOURCE,
          "n_type", NameType.SCIENTIFIC
      ));

      c.commit();
    }
  }

  void loadGlobalData2() throws SQLException, IOException {
    try (Connection c = sqlSessionFactorySupplier.get().openSession(false).getConnection()) {
      PgConnection pgc = InitDbUtils.toPgConnection(c);
      copyGlobalTable(pgc, "name_match");
      c.commit();
    }
  }

  private void copyDataset(PgConnection pgc, int key) throws IOException, SQLException {
    LOG.debug("Copy dataset {}", key);
    copyPartitionedTable(pgc, "verbatim", key, ImmutableMap.of("dataset_key", key), Collections.emptyList());
    copyPartitionedTable(pgc, "verbatim_source", key, ImmutableMap.of("dataset_key", key, "source_entity", "NAME_USAGE"), Collections.emptyList());
    copyPartitionedTable(pgc, "reference", key, datasetEntityDefaults(key), Collections.emptyList());

    List<CsvFunction> nameFuncs = testData.parseNames ?
      List.of(new NameParserFunc(), new NameNormalizerFunc(), new PgAuthorshipNormalizer()) : List.of(new NameNormalizerFunc());
    copyPartitionedTable(pgc, "name", key,
      datasetEntityDefaults(key, ImmutableMap.<String, Object>of(
        "origin", Origin.SOURCE
      )), nameFuncs
    );
    copyPartitionedTable(pgc, "name_rel", key, datasetEntityDefaults(key), Collections.emptyList());
    copyPartitionedTable(pgc, "type_material", key, datasetEntityDefaults(key), Collections.emptyList());
    copyPartitionedTable(pgc, "name_usage", key,
      datasetEntityDefaults(key, ImmutableMap.<String, Object>of("origin", Origin.SOURCE)), Collections.emptyList()
    );
    copyPartitionedTable(pgc, "distribution", key, datasetEntityDefaults(key), Collections.emptyList());
    copyPartitionedTable(pgc, "vernacular_name", key, datasetEntityDefaults(key), Collections.emptyList());
    copyPartitionedTable(pgc, "taxon_metrics", key, datasetDefaults(key), Collections.emptyList());
  }

  class NameParserFunc implements CsvFunction {
    private final List<String> columns;

    NameParserFunc() {
      this.columns = List.copyOf(name2columns("Abies", TestEntityGenerator.NAME2).keySet());
    }

    @Override
    public List<String> columns() {
      return columns;
    }

    @Override
    public LinkedHashMap<String, String> apply(String[] row) {
      try {
        String name = row[testData.sciNameColumn];
        var auth = testData.authorNameColumn == null ? null : row[testData.authorNameColumn];
        var rank = testData.rankColumn == null ? null : row[testData.rankColumn];
        var code = testData.codeColumn == null ? null : row[testData.codeColumn];
        Name pn = NameParser.PARSER.parse(name, auth,
          rank == null ? null : Rank.valueOf(rank),
          code == null ? null : NomCode.valueOf(code),
          IssueContainer.VOID
        ).get().getName();

        return name2columns(name, pn);

      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    LinkedHashMap<String, String> name2columns(String name, Name pn) {
      var data = new LinkedHashMap<String, String>();
      data.put("type", pn.getType().name());
      data.put("uninomial", pn.getUninomial());
      data.put("genus", pn.getGenus());
      data.put("infrageneric_epithet", pn.getInfragenericEpithet());
      data.put("specific_epithet", pn.getSpecificEpithet());
      data.put("infraspecific_epithet", pn.getInfraspecificEpithet());
      data.put("cultivar_epithet", pn.getCultivarEpithet());
      data.put("basionym_authors", str(pn.getBasionymAuthorship().getAuthors()));
      data.put("basionym_ex_authors", str(pn.getBasionymAuthorship().getAuthors()));
      data.put("basionym_year", pn.getBasionymAuthorship().getYear());
      data.put("combination_authors", str(pn.getCombinationAuthorship().getAuthors()));
      data.put("combination_ex_authors", str(pn.getCombinationAuthorship().getAuthors()));
      data.put("combination_year", pn.getCombinationAuthorship().getYear());
      data.put("notho", str(pn.getNotho()));
      data.put("candidatus", str(pn.isCandidatus()));
      data.put("unparsed", pn.getUnparsed());
      return data;
    }
  }
  class NameNormalizerFunc implements CsvFunction {
    private final String column = "scientific_name_normalized";

    @Override
    public List<String> columns() {
      return List.of(column);
    }

    @Override
    public LinkedHashMap<String, String> apply(String[] row) {
      var data = new LinkedHashMap<String, String>();
      String val = null;
      if (testData.sciNameColumn != null) {
        val = SciNameNormalizer.normalize(row[testData.sciNameColumn]);
      }
      data.put("scientific_name_normalized", val);
      return data;
    }
  }

  private static String str(Boolean val) {
    return val == null ? null : (val ? "t" : "f");
  }
  private static String str(Enum<?> val) {
    return val == null ? null : val.name();
  }
  private static String str(List<String> arr) {
    return PgCopyUtils.buildPgArray(arr.toArray(new String[0]));
  }

  private Map<String, Object> datasetDefaults(int datasetKey) {
    return ImmutableMap.of("dataset_key", datasetKey);
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

  private boolean copyGlobalTable(PgConnection pgc, String table) throws IOException, SQLException {
    return copyTable(pgc, table + ".csv", table, Collections.emptyMap(), Collections.emptyList());
  }

  private boolean copyGlobalTable(PgConnection pgc, String table, Map<String, Object> defaults) throws IOException, SQLException {
    return copyTable(pgc, table + ".csv", table, defaults, Collections.emptyList());
  }

  private boolean copyPartitionedTable(PgConnection pgc, String table, int datasetKey, Map<String, Object> defaults, List<CsvFunction> funcs) throws IOException, SQLException {
    return copyTable(pgc, table + "_" + datasetKey + ".csv", table, defaults, funcs);
  }

  private boolean copyTable(PgConnection pgc, String filename, String table, Map<String, Object> defaults, List<CsvFunction> funcs)
      throws IOException, SQLException {
    String resource = "/test-data/" + testData.name.toLowerCase() + "/" + filename;
    URL url = PgCopyUtils.class.getResource(resource);
    if (url != null) {
      // global defaults to add?
      if (testData.defaultValues.containsKey(table)) {
        defaults = new HashMap<>(defaults);
        defaults.putAll(testData.defaultValues.get(table));
      }
      PgCopyUtils.loadCSV(pgc, table, resource, defaults, funcs);
      return true;
    }
    return false;
  }
}
