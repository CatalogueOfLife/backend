package life.catalogue.command;

import com.google.common.base.Preconditions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.setup.Bootstrap;
import it.unimi.dsi.fastutil.ints.IntSet;
import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.concurrent.ExecutorUtils;
import life.catalogue.common.concurrent.NamedThreadFactory;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.util.LoggingUtils;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.SqlSessionFactoryWithPath;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.matching.DatasetMatcher;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.matching.RematchJob;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class NamesIndexCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(NamesIndexCmd.class);
  private static final String ARG_THREADS = "t";
  private static final String BUILD_SCHEMA = "build";
  int threads = 4;

  public NamesIndexCmd() {
    super("nidx", "Rebuilt names index and rematch all datasets");

  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds indexing options
    subparser.addArgument("-"+ ARG_THREADS)
      .dest(ARG_THREADS)
      .type(Integer.class)
      .required(false)
      .help("number of threads to use for rematching. Defaults to " + threads);
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Rebuilt names index and rematch all datasets with data in pg schema %s in db %s.\n", BUILD_SCHEMA, cfg.db.database);
  }

  private static File indexFileToday(WsServerConfig cfg){
    File f = null;
    if (cfg.namesIndexFile != null) {
      String date = DateTimeFormatter.ISO_DATE.format(LocalDate.now());
      f = new File(cfg.namesIndexFile.getParent(), "nidx-" + date);
      if (f.exists()) {
        throw new IllegalStateException("NamesIndex file already exists: " + f.getAbsolutePath());
      }
      f.getParentFile().mkdirs();
      System.out.println("Creating new names index at " + f.getAbsolutePath());
    } else {
      System.out.println("Creating new in memory names index");
    }
    return f;
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    if (namespace.getInt(ARG_THREADS) != null) {
      threads = namespace.getInt(ARG_THREADS);
      Preconditions.checkArgument(threads > 0, "Needs at least one matcher thread");
    }
    LOG.warn("Rebuilt names index and rematch all datasets with data in pg schema {} with {} threads", BUILD_SCHEMA, threads);

    try (HikariDataSource dataSource = cfg.db.pool()) {
      // use a factory that changes the default pg search_path to "build" so we don't interfer with the index current live
      SqlSessionFactory factory = new SqlSessionFactoryWithPath(MybatisFactory.configure(dataSource, "namesIndexCmd"), BUILD_SCHEMA);

      LOG.info("Prepare pg schema {}", BUILD_SCHEMA);
      try (Connection c = dataSource.getConnection();
           Statement st = c.createStatement()
      ) {
        // setup build schema
        st.execute(String.format("DROP SCHEMA IF EXISTS %s CASCADE", BUILD_SCHEMA));
        st.execute(String.format("CREATE SCHEMA %s", BUILD_SCHEMA));
        st.execute(String.format("CREATE TABLE %s.name_match (LIKE public.name_match INCLUDING DEFAULTS)", BUILD_SCHEMA));
        st.execute(String.format("CREATE TABLE %s.names_index (LIKE public.names_index INCLUDING DEFAULTS)", BUILD_SCHEMA));
        st.execute(String.format("CREATE SEQUENCE %s.names_index_id_seq START 1", BUILD_SCHEMA));
        st.execute(String.format("ALTER TABLE %s.names_index ALTER COLUMN id SET DEFAULT nextval('%s.names_index_id_seq')", BUILD_SCHEMA, BUILD_SCHEMA));
      }

      NameIndex ni = NameIndexFactory.persistentOrMemory(indexFileToday(cfg), factory, AuthorshipNormalizer.INSTANCE);
      ni.start();

      IntSet keys;
      try (SqlSession session = factory.openSession()) {
        keys = DaoUtils.listDatasetWithPartitions(session);
      }

      final AtomicInteger counter = new AtomicInteger(0);
      final AtomicInteger total = new AtomicInteger(0);
      final AtomicInteger nomatch = new AtomicInteger(0);
      ExecutorService exec = Executors.newFixedThreadPool(threads, new NamedThreadFactory("dataset-matcher"));
      for (int key : keys) {
        CompletableFuture.supplyAsync(() -> rematchDataset(key, factory, ni), exec)
          .exceptionally(ex -> {
            counter.incrementAndGet();
            LOG.error("Error matching dataset {}", key, ex.getCause());
            return null;
          })
          .thenAccept(m -> {
            LOG.info("Indexed {}/{} dataset {}. Total usages {} with {} not matching",
              counter.incrementAndGet(), keys.size(), key, total.addAndGet(m.getTotal()), nomatch.addAndGet(m.getNomatch())
            );
          });
      }
      ExecutorUtils.shutdown(exec);

      LOG.info("Successfully rebuild names index with final size {}, rematching all {} datasets",
        ni.size(), counter);
    }
  }

  private DatasetMatcher rematchDataset(int key, SqlSessionFactory factory, NameIndex ni){
    LoggingUtils.setDatasetMDC(key, getClass());
    DatasetMatcher matcher = new DatasetMatcher(factory, ni);
    matcher.match(key, true);
    LoggingUtils.setDatasetMDC(key, getClass());
    return matcher;
  }

}
