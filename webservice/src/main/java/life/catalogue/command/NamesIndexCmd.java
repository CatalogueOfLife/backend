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
      .help("number of threads to use for rematching. Defaults to 1");
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
    }
    return f;
  }

  public void prePromt(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg){
    File idxFile = indexFileToday(cfg);
    System.out.println("Creating new names index at " + idxFile.getAbsolutePath());
    LOG.info("Rebuilt names index and rematch all datasets with data in pg schema {}", BUILD_SCHEMA);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    int threads = 4;
    if (namespace.getInt(ARG_THREADS) != null) {
      threads = namespace.getInt(ARG_THREADS);
      Preconditions.checkArgument(threads > 0, "Needs at least one matcher thread");
    }

    try (HikariDataSource dataSource = cfg.db.pool()) {
      SqlSessionFactory factory = MybatisFactory.configure(dataSource, "namesIndexCmd");
      File idxFile = indexFileToday(cfg);
      NameIndex ni = NameIndexFactory.persistentOrMemory(idxFile, factory, AuthorshipNormalizer.INSTANCE);
      LOG.warn("Rebuilt names index and rematch all datasets with data in pg schema {}", BUILD_SCHEMA);

      LOG.info("Prepare pg schema {}", BUILD_SCHEMA);
      try (Connection c = dataSource.getConnection();
           Statement st = c.createStatement()
      ) {
        // setup build schema
        st.execute(String.format("DROP SCHEMA IF EXIST %s CASCADE", BUILD_SCHEMA));
        st.execute(String.format("CREATE SCHEMA %s", BUILD_SCHEMA));
        st.execute(String.format("CREATE TABLE %s.names_index (LIKE public.names_index INCLUDING DEFAULTS)", BUILD_SCHEMA));
        st.execute(String.format("CREATE TABLE %s.name_match (LIKE public.name_match INCLUDING DEFAULTS)", BUILD_SCHEMA));
        c.commit();
      }

      SqlSessionFactory buildFactory = new SqlSessionFactoryWithPath(factory, BUILD_SCHEMA);
      RematchJob job = RematchJob.all(Users.MATCHER, buildFactory, ni);
      job.execute();

      IntSet keys;
      try (SqlSession session = factory.openSession()) {
        keys = DaoUtils.listDatasetWithPartitions(session);
      }

      final AtomicInteger counter = new AtomicInteger(0);
      final AtomicInteger total = new AtomicInteger(0);
      final AtomicInteger nomatch = new AtomicInteger(0);
      ExecutorService exec = Executors.newFixedThreadPool(threads, new NamedThreadFactory("dataset-matcher"));
      for (int key : keys) {
        CompletableFuture.supplyAsync(() -> rematchDataset(key, buildFactory, ni), exec)
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

      LOG.info("Successfully rebuild names index with final size {} at {}, rematching all {} datasets",
        ni.size(), idxFile, counter);
    }
  }

  DatasetMatcher rematchDataset(int key, SqlSessionFactory factory, NameIndex ni){
    LoggingUtils.setDatasetMDC(key, getClass());
    DatasetMatcher matcher = new DatasetMatcher(factory, ni);
    matcher.match(key, true);
    LoggingUtils.setDatasetMDC(key, getClass());
    return matcher;
  }

}
