package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.config.MatchingConfig;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.dao.TaxonMetricsBuilder;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.PgDbConfig;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.db.mapper.TaxonMetricsMapper;
import life.catalogue.matching.nidx.NamesIndexConfig;
import life.catalogue.pgcopy.PgCopyUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariDataSource;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Builds the data artifact for a CLB "release in a box" bundle, see docs/BUNDLE.md.
 *
 * Runs against a full ChecklistBank database and produces a directory that a
 * {@link life.catalogue.WsBundleServer} can be pointed at:
 *
 * <pre>
 *   release.dump            pg_dump -Fc of a database holding just this one release
 *   nidx/                   names index store
 *   matcher/{releaseKey}/   the memory mapped usage matcher store
 *   metrics/                the file based dataset metrics of the release
 *   bundle.json             what was built, when and from where
 * </pre>
 *
 * Because all data tables are hash partitioned on dataset_key there is no partition to detach, so the
 * release is filtered out row by row with binary COPY into a temporary database, which is then dumped
 * with pg_dump and dropped again. The detour is what makes the artifact a plain, self describing dump
 * the stock postgres image can restore without any of our code.
 */
public class BundleBuildCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(BundleBuildCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_DIR = "dir";
  private static final String ARG_DELETE = "delete";
  private static final String ARG_IMAGE = "image";

  /** Where the generated docker-compose.yml pulls the app image from unless --image says otherwise. */
  static final String DEFAULT_IMAGE = "ghcr.io/catalogueoflife/clb-bundle:latest";

  /** Runtime files copied into every artifact so a bundle is usable as downloaded. */
  private static final String TEMPLATE_DIR = "life/catalogue/bundle/";
  private static final List<String> TEMPLATES = List.of("config.yml", "docker-compose.yml", "restore.sh", "README.md");

  /**
   * Every dataset row the release slice needs a foreign key to satisfy: the release itself, the project it
   * came from, the subject datasets of its sectors, its sources, and whatever those in turn were released
   * from. depth counts up the source_key chain so ancestors can be inserted first - dataset.source_key is a
   * self reference and copy checks it row by row.
   */
  private static final String KEY_CLOSURE =
    "WITH RECURSIVE closure(k, depth) AS (" +
    "  SELECT key, 0 FROM dataset WHERE key = %1$d" +
    "  UNION ALL SELECT subject_dataset_key, 0 FROM sector WHERE dataset_key = %1$d" +
    "  UNION ALL SELECT key, 0 FROM dataset_source WHERE dataset_key = %1$d" +
    "  UNION ALL SELECT d.source_key, c.depth+1 FROM dataset d JOIN closure c ON d.key = c.k WHERE d.source_key IS NOT NULL" +
    ")";

  private File dir;
  private int key;
  private long nidxCount;
  private int nidxCapacity;
  private Dataset release;
  private Dataset project;
  private String tmpDbName;

  public BundleBuildCmd() {
    super("bundleBuild", "Build the data artifact of a single release CLB bundle");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--" + ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(true)
      .help("Key of the release to bundle");
    subparser.addArgument("--" + ARG_DIR)
      .dest(ARG_DIR)
      .type(String.class)
      .required(true)
      .help("Output directory for the bundle data artifact");
    subparser.addArgument("--" + ARG_DELETE)
      .dest(ARG_DELETE)
      .action(Arguments.storeTrue())
      .help("Wipe an existing output directory first");
    subparser.addArgument("--" + ARG_IMAGE)
      .dest(ARG_IMAGE)
      .type(String.class)
      .required(false)
      .help("App image the generated docker-compose.yml pulls, default " + DEFAULT_IMAGE);
  }

  @Override
  public String describeCmd(Namespace ns, WsServerConfig cfg) {
    return String.format("Build a release bundle for dataset %s from db %s on %s.\n",
      ns.getInt(ARG_KEY), cfg.db.database, cfg.db.host);
  }

  @Override
  public void execute() throws Exception {
    key = ns.getInt(ARG_KEY);
    dir = new File(ns.getString(ARG_DIR));
    if (dir.exists() && ns.getBoolean(ARG_DELETE)) {
      LOG.info("Delete existing bundle dir {}", dir);
      FileUtils.deleteDirectory(dir);
    }
    if (!dir.mkdirs() && !dir.isDirectory()) {
      throw new IllegalStateException("Unable to create bundle dir " + dir.getAbsolutePath());
    }
    readDatasets();
    tmpDbName = "clb_bundle_" + key;

    try {
      createTmpDb();
      try (var tmpPool = tmpPgConfig().pool()) {
        var tmpFactory = MybatisFactory.configure(tmpPool, "bundleBuild");
        copyData(tmpFactory);
        buildTaxonMetrics(tmpFactory);
        buildStores(tmpFactory);
      }
      copyFileMetrics();
      pgDump();
      writeManifest();
      writeRuntimeFiles();
      LOG.info("Bundle for release {} built at {}", key, dir.getAbsolutePath());
      System.out.println("Done. Bundle at " + dir.getAbsolutePath());

    } finally {
      dropTmpDb();
    }
  }

  private void readDatasets() {
    try (SqlSession session = factory.openSession()) {
      var dm = session.getMapper(DatasetMapper.class);
      release = dm.get(key);
      if (release == null) {
        throw new IllegalArgumentException("Dataset " + key + " does not exist");
      }
      if (!release.getOrigin().isRelease()) {
        LOG.warn("Dataset {} is not a release but a {}. Bundling it anyway.", key, release.getOrigin());
      } else {
        project = dm.get(release.getSourceKey());
      }
      LOG.info("Bundle release {}: {}", key, release.getTitle());
    }
  }

  // ---------------------------------------------------------------- temp db

  private PgConfig tmpPgConfig() {
    PgConfig c = new PgConfig();
    c.host = cfg.db.host;
    c.port = cfg.db.port;
    c.database = tmpDbName;
    c.user = cfg.db.user;
    c.password = cfg.db.password;
    c.maximumPoolSize = 4;
    c.minimumIdle = 1;
    return c;
  }

  private PgDbConfig tmpDbConfig() {
    PgDbConfig c = new PgDbConfig();
    c.database = tmpDbName;
    c.user = cfg.db.user;
    c.password = cfg.db.password;
    return c;
  }

  private void createTmpDb() throws Exception {
    LOG.info("Create temporary bundle database {}", tmpDbName);
    try (Connection con = cfg.db.connect(cfg.adminDb)) {
      // createDatabase drops an existing one of the same name first
      PgUtils.createDatabase(con, tmpDbName, cfg.db.user);
    }
    try (Connection con = cfg.db.connect(tmpDbConfig())) {
      ScriptRunner runner = PgConfig.scriptRunner(con);
      runner.runScript(Resources.getResourceAsReader(InitDbUtils.SCHEMA_FILE));
      runner.runScript(Resources.getResourceAsReader(InitDbUtils.DATA_FILE));
    }
    try (var pool = tmpPgConfig().pool()) {
      // a bundle serves one dataset, so a single partition per table is all it ever needs
      Partitioner.createPartitions(MybatisFactory.configure(pool, "bundleInit"), 1);
    }
  }

  private void dropTmpDb() {
    if (tmpDbName == null) return;
    try (Connection con = cfg.db.connect(cfg.adminDb); Statement st = con.createStatement()) {
      LOG.info("Drop temporary bundle database {}", tmpDbName);
      st.execute("DROP DATABASE IF EXISTS \"" + tmpDbName + "\"");
    } catch (Exception e) {
      LOG.error("Failed to drop temporary bundle database {}", tmpDbName, e);
    }
  }

  // ---------------------------------------------------------------- data copy

  /**
   * The global tables to copy, in an order that satisfies the foreign keys. Every entry is a where clause
   * (and optional order by) appended to a plain select of all columns.
   */
  private List<String[]> globalTables() {
    String closure = String.format(KEY_CLOSURE, key);
    List<String[]> tables = new ArrayList<>();
    // ancestors first, see KEY_CLOSURE
    tables.add(new String[]{"dataset", closure +
      " SELECT %s FROM dataset WHERE key IN (SELECT k FROM closure WHERE k IS NOT NULL)" +
      " ORDER BY (SELECT max(depth) FROM closure c WHERE c.k = dataset.key) DESC"});
    tables.add(new String[]{"dataset_citation", closure +
      " SELECT %s FROM dataset_citation WHERE dataset_key IN (SELECT k FROM closure WHERE k IS NOT NULL)"});
    tables.add(new String[]{"dataset_archive", closure +
      " SELECT %s FROM dataset_archive WHERE key IN (SELECT k FROM closure WHERE k IS NOT NULL)"});
    tables.add(new String[]{"dataset_archive_citation", closure +
      " SELECT %s FROM dataset_archive_citation WHERE dataset_key IN (SELECT k FROM closure WHERE k IS NOT NULL)"});
    tables.add(new String[]{"dataset_source", "SELECT %s FROM dataset_source WHERE dataset_key = " + key});
    tables.add(new String[]{"dataset_source_citation", "SELECT %s FROM dataset_source_citation WHERE release_key = " + key});
    tables.add(new String[]{"dataset_patch", "SELECT %s FROM dataset_patch WHERE dataset_key IN (" + keyAndProject() + ")"});
    tables.add(new String[]{"dataset_import", "SELECT %s FROM dataset_import WHERE dataset_key = " + key
      + (releaseAttempt() == null ? "" : " OR (dataset_key = " + project.getKey() + " AND attempt = " + releaseAttempt() + ")")});
    tables.add(new String[]{"sector", "SELECT %s FROM sector WHERE dataset_key = " + key});
    tables.add(new String[]{"sector_import", "SELECT %s FROM sector_import WHERE dataset_key = " + key});
    tables.add(new String[]{"sector_publisher", "SELECT %s FROM sector_publisher WHERE dataset_key = " + key});
    tables.add(new String[]{"decision", "SELECT %s FROM decision WHERE dataset_key = " + key});
    // only the canonical names this release actually matched to - name_match has a foreign key onto them
    tables.add(new String[]{"names_index", "SELECT %s FROM names_index WHERE id IN"
      + " (SELECT DISTINCT index_id FROM name_match WHERE dataset_key = " + key + " AND index_id IS NOT NULL)"});
    return tables;
  }

  private String keyAndProject() {
    return project == null ? String.valueOf(key) : key + ", " + project.getKey();
  }

  private Integer releaseAttempt() {
    return project == null ? null : release.getAttempt();
  }

  private void copyData(SqlSessionFactory tmpFactory) throws Exception {
    File dumps = new File(dir, "pgdumps");
    dumps.mkdirs();
    try (PgConnection src = cfg.db.connect();
         PgConnection tgt = cfg.db.connect(tmpDbConfig());
         SqlSession session = factory.openSession()
    ) {
      var dpm = session.getMapper(DatasetPartitionMapper.class);
      disableUsageCountTriggers(tgt, true);
      try {
        for (String[] t : globalTables()) {
          long n = copyTable(src, tgt, dpm, dumps, t[0], t[1]);
          if ("names_index".equals(t[0])) {
            nidxCount = n;
          }
        }
        for (String table : DatasetPartitionMapper.PARTITIONED_TABLES) {
          copyTable(src, tgt, dpm, dumps, table, "SELECT %s FROM " + table + " WHERE dataset_key = " + key);
        }
      } finally {
        disableUsageCountTriggers(tgt, false);
      }
    }
    FileUtils.deleteDirectory(dumps);

    // per dataset sequences and the usage counter the triggers did not maintain
    try (SqlSession session = tmpFactory.openSession(true)) {
      var dpm = session.getMapper(DatasetPartitionMapper.class);
      dpm.createSequences(key);
      dpm.updateSequences(key);
      dpm.deleteUsageCounter(key);
      int usages = dpm.updateUsageCounter(key);
      LOG.info("Bundled {} name usages for release {}", usages, key);
    }
  }

  private long copyTable(PgConnection src, PgConnection tgt, DatasetPartitionMapper dpm, File dumps,
                         String table, String sqlTemplate) throws IOException, SQLException {
    List<String> columns = dpm.columns(table);
    if (columns.isEmpty()) {
      throw new IllegalStateException("Table " + table + " does not exist");
    }
    String cols = columns.stream().map(c -> "\"" + c + "\"").reduce((a, b) -> a + "," + b).orElseThrow();
    File f = new File(dumps, table + ".bin");
    long dumped = PgCopyUtils.dumpBinary(src, String.format(sqlTemplate, cols), f);
    if (dumped > 0) {
      PgCopyUtils.loadBinary(tgt, table, columns, f);
    }
    LOG.info("Copied {} {} records", dumped, table);
    f.delete();
    return dumped;
  }

  /**
   * The per partition name_usage statement triggers build a transition table of everything a COPY inserts,
   * which for a whole release is a lot of memory for a counter we recompute afterwards anyway.
   */
  private void disableUsageCountTriggers(Connection con, boolean disable) throws SQLException {
    try (Statement st = con.createStatement()) {
      var rs = st.executeQuery("SELECT inhrelid::regclass::text FROM pg_inherits WHERE inhparent = 'name_usage'::regclass");
      List<String> partitions = new ArrayList<>();
      while (rs.next()) {
        partitions.add(rs.getString(1));
      }
      rs.close();
      for (String p : partitions) {
        try (Statement st2 = con.createStatement()) {
          st2.execute("ALTER TABLE " + p + (disable ? " DISABLE" : " ENABLE") + " TRIGGER USER");
        }
      }
    }
  }

  // ---------------------------------------------------------------- metrics & stores

  private void buildTaxonMetrics(SqlSessionFactory tmpFactory) {
    int existing;
    try (SqlSession session = tmpFactory.openSession()) {
      existing = session.getMapper(TaxonMetricsMapper.class).countByDataset(key);
    }
    if (existing > 0) {
      LOG.info("Bundle ships {} taxon metrics copied from the source database", existing);
    } else {
      LOG.info("No taxon metrics for release {}, build them in the bundle database", key);
      TaxonMetricsBuilder.rebuildMetrics(tmpFactory, key);
    }
  }

  /**
   * Builds the names index and usage matcher stores from the bundle database rather than the source one, so
   * the nidx ids baked into the matcher store are the very ids the shipped names_index rows carry.
   */
  private void buildStores(SqlSessionFactory tmpFactory) throws Exception {
    NamesIndexConfig nCfg = new NamesIndexConfig();
    nCfg.file = new File(dir, "nidx");
    nCfg.verification = false;
    // The chronicle store's capacity is fixed when the file is created, so size it for the names this
    // release actually matched to rather than inheriting the source index' capacity, which covers all of
    // ChecklistBank. Headroom is for the names a running bundle adds when it matches something new.
    nidxCapacity = (int) Math.max(1_000, Math.min(Integer.MAX_VALUE, Math.round(nidxCount * 1.25) + 10_000));
    nCfg.maxEntries = nidxCapacity;
    LOG.info("Size the bundle names index for {} entries, {} of them shipped", nidxCapacity, nidxCount);

    MatchingConfig mCfg = new MatchingConfig();
    mCfg.storageDir = new File(dir, "matcher");
    mCfg.uploadDir = cfg.matching.uploadDir;
    mCfg.storageDir.mkdirs();

    // the bundle database is the names index source too, so the nidx ids in the store are the shipped ones
    MatcherStoreBuilder.build(key, nCfg, mCfg, tmpFactory, tmpFactory);
  }

  /**
   * The file based dataset metrics of a release live under the mother project's key and import attempt,
   * see DatasetImportDao.getReleaseAttempt, so that is what gets copied into the bundle's metrics repo.
   */
  private void copyFileMetrics() throws IOException {
    if (project == null || release.getAttempt() == null) {
      LOG.info("No project attempt for dataset {}, skip file metrics", key);
      return;
    }
    final int attempt = release.getAttempt();
    try (SqlSession session = factory.openSession()) {
      if (session.getMapper(DatasetImportMapper.class).get(project.getKey(), attempt) == null) {
        LOG.warn("No import metrics for project {} attempt {}, skip file metrics", project.getKey(), attempt);
        return;
      }
    }
    File repo = new File(dir, "metrics");
    var srcDao = new FileMetricsDatasetDao(factory, cfg.metricsRepo);
    var tgtDao = new FileMetricsDatasetDao(factory, repo);
    File tgtDir = tgtDao.subdir(project.getKey());
    tgtDir.mkdirs();
    copyIfExists(srcDao.namesFile(project.getKey(), attempt), tgtDao.namesFile(project.getKey(), attempt));
    copyIfExists(srcDao.treeFile(project.getKey(), attempt), tgtDao.treeFile(project.getKey(), attempt));
  }

  private void copyIfExists(File src, File tgt) throws IOException {
    if (src.exists()) {
      Files.copy(src.toPath(), tgt.toPath(), StandardCopyOption.REPLACE_EXISTING);
      LOG.info("Copied metrics file {}", src.getName());
    } else {
      LOG.warn("Metrics file {} does not exist", src);
    }
  }

  // ---------------------------------------------------------------- dump

  private void pgDump() throws Exception {
    File out = new File(dir, "release.dump");
    LOG.info("Dump bundle database {} to {}", tmpDbName, out);
    List<String> cmd = List.of("pg_dump", "-Fc", "-Z", "6",
      "-h", cfg.db.host, "-p", String.valueOf(cfg.db.port), "-U", cfg.db.user,
      "-f", out.getAbsolutePath(), tmpDbName);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectErrorStream(true);
    if (cfg.db.password != null) {
      pb.environment().put("PGPASSWORD", cfg.db.password);
    }
    Process p;
    try {
      p = pb.start();
    } catch (IOException e) {
      throw new IllegalStateException("pg_dump is required to build a bundle, but could not be executed. "
        + "Install the postgres client tools and make sure pg_dump is on the PATH.", e);
    }
    String output = new String(p.getInputStream().readAllBytes());
    if (!p.waitFor(12, TimeUnit.HOURS) || p.exitValue() != 0) {
      throw new IllegalStateException("pg_dump failed with exit code " + p.exitValue() + ": " + output);
    }
    LOG.info("Wrote {} ({} bytes)", out, out.length());
  }

  private void writeManifest() throws IOException {
    File f = new File(dir, "bundle.json");
    String json = String.format("{%n"
      + "  \"releaseKey\": %d,%n"
      + "  \"title\": %s,%n"
      + "  \"alias\": %s,%n"
      + "  \"projectKey\": %s,%n"
      + "  \"attempt\": %s,%n"
      + "  \"namesIndexEntries\": %d,%n"
      + "  \"built\": \"%s\",%n"
      + "  \"source\": \"%s\",%n"
      + "  \"version\": %s%n"
      + "}%n",
      key,
      quote(release.getTitle()),
      quote(release.getAlias()),
      project == null ? "null" : String.valueOf(project.getKey()),
      release.getAttempt() == null ? "null" : String.valueOf(release.getAttempt()),
      nidxCapacity,
      Instant.now(),
      cfg.db.host + "/" + cfg.db.database,
      quote(cfg.versionString()));
    Files.writeString(f.toPath(), json);
  }

  /**
   * Copies the compose file, the app config, the postgres restore hook and a README into the artifact,
   * with the release key and image already filled in. That is what makes a downloaded bundle runnable
   * as is - dropwizard does not substitute environment variables into its yaml, so the release key has
   * to be baked in here rather than passed at run time.
   */
  private void writeRuntimeFiles() throws IOException {
    final String image = ObjectUtils.coalesce(ns.getString(ARG_IMAGE), DEFAULT_IMAGE);
    final String title = ObjectUtils.coalesce(release.getAlias(), release.getTitle(), "COL release " + key);
    for (String name : TEMPLATES) {
      String content = new String(Resources.getResourceAsStream(TEMPLATE_DIR + name).readAllBytes(), StandardCharsets.UTF_8)
        .replace("{{RELEASE_KEY}}", String.valueOf(key))
        .replace("{{IMAGE}}", image)
        .replace("{{TITLE}}", title)
        .replace("{{BUILT}}", DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now()));
      File f = new File(dir, name);
      Files.writeString(f.toPath(), content);
      if (name.endsWith(".sh") && !f.setExecutable(true)) {
        LOG.warn("Failed to make {} executable", f);
      }
    }
    LOG.info("Wrote {} into the bundle, app image {}", TEMPLATES, image);
  }

  private static String quote(String s) {
    return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
