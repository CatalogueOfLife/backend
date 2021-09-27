package life.catalogue.command;

import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.PathUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.dao.NameDao;
import life.catalogue.dao.Partitioner;
import life.catalogue.dao.TaxonDao;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.EsUtil;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.es.nu.NameUsageIndexServiceEs;
import life.catalogue.matching.DatasetMatcher;
import life.catalogue.matching.NameIndex;
import life.catalogue.matching.NameIndexFactory;
import life.catalogue.postgres.IntSerial;
import life.catalogue.postgres.PgCopyUtils;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.elasticsearch.client.RestClient;
import org.gbif.nameparser.api.NameType;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Function;

/**
 * Command to initialise a new database schema.
 */
public class InitDbCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(InitDbCmd.class);

  public InitDbCmd() {
    super("initdb", "Initialises a new database schema");
  }
  
  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Initialising database %s on %s.\n", cfg.db.database, cfg.db.host);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    execute(cfg);
  }
  
  public static void execute(WsServerConfig cfg) throws Exception {
    LOG.info("Starting database initialisation with admin connection {}", cfg.adminDb);
    try (Connection con = cfg.db.connect(cfg.adminDb);
         Statement st = con.createStatement()
    ) {
      LOG.info("Drop existing database {}", cfg.db.database);
      st.execute("DROP DATABASE IF EXISTS \"" + cfg.db.database + "\"");
      
      LOG.info("Create new database {}", cfg.db.database);
      st.execute("CREATE DATABASE  \"" + cfg.db.database + "\"" +
          " WITH ENCODING UTF8 LC_COLLATE 'C' LC_CTYPE 'C' OWNER " + cfg.db.user + " TEMPLATE template0");

      LOG.info("Use UTC timezone for {}", cfg.db.database);
      st.execute("ALTER DATABASE  \"" + cfg.db.database + "\" SET timezone TO 'UTC'");
    }
    
    try (Connection con = cfg.db.connect()) {
      ScriptRunner runner = PgConfig.scriptRunner(con);
      // run sql schema
      exec(InitDbUtils.SCHEMA_FILE, runner, con, Resources.getResourceAsReader(InitDbUtils.SCHEMA_FILE));
      // add common data
      exec(InitDbUtils.DATA_FILE, runner, con, Resources.getResourceAsReader(InitDbUtils.DATA_FILE));
      // register known datasets
      InitDbUtils.insertDatasets(InitDbUtils.toPgConnection(con));
    }
    
    // cleanup names index
    if (cfg.namesIndexFile != null && cfg.namesIndexFile.exists()) {
      LOG.info("Clear names index at {}", cfg.namesIndexFile.getAbsolutePath());
      if (!cfg.namesIndexFile.delete()) {
        LOG.error("Unable to delete names index at {}", cfg.namesIndexFile.getAbsolutePath());
        throw new IllegalStateException("Unable to delete names index at " + cfg.namesIndexFile.getAbsolutePath());
      }
    }
    
    // clear images, scratch dir & archive repo
    LOG.info("Clear image cache {}", cfg.img.repo);
    PathUtils.cleanDirectory(cfg.img.repo);
  
    LOG.info("Clear scratch dir {}", cfg.normalizer.scratchDir);
    if (cfg.normalizer.scratchDir.exists()) {
      FileUtils.cleanDirectory(cfg.normalizer.scratchDir);
    }
  
    LOG.info("Clear archive repo {}", cfg.normalizer.archiveDir);
    if (cfg.normalizer.archiveDir.exists()) {
      FileUtils.cleanDirectory(cfg.normalizer.archiveDir);
    }
  
    LOG.info("Clear metrics repo {}", cfg.metricsRepo);
    if (cfg.metricsRepo.exists()) {
      FileUtils.cleanDirectory(cfg.metricsRepo);
    }

    // create managed partitions
    HikariConfig hikari = cfg.db.hikariConfig();
    try (HikariDataSource dataSource = new HikariDataSource(hikari)) {
      // configure single mybatis session factory
      final SqlSessionFactory factory = MybatisFactory.configure(dataSource, "init");
    
      // add col dataset and partitions
      try (SqlSession session = factory.openSession()) {
        setupColPartition(session);
        session.getMapper(DatasetPartitionMapper.class).createManagedSequences(Datasets.COL);
        session.commit();
      }
    }
  }

  public static void setupColPartition(SqlSession session) {
    Partitioner.partition(session, Datasets.COL, DatasetOrigin.MANAGED);
    Partitioner.attach(session, Datasets.COL, DatasetOrigin.MANAGED);
    session.commit();
  }
  
  public static void exec(String name, ScriptRunner runner, Connection con, Reader reader) {
    try {
      LOG.info("Executing {}", name);
      runner.runScript(reader);
      con.commit();
    } catch (RuntimeException e) {
      LOG.error("Failed to execute {}", name);
      throw e;
      
    } catch (Exception e) {
      LOG.error("Failed to execute {}", name);
      throw new RuntimeException("Fail to execute sql file: " + name, e);
    }
  }
}
