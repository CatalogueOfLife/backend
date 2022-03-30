package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command to initialise a new database schema.
 */
public class InitDbCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(InitDbCmd.class);
  private static final String ARG_DATASET = "dataset";

  public InitDbCmd() {
    super("initdb", "Initialises a new database schema");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_DATASET, "-d")
             .dest(ARG_DATASET)
             .type(String.class)
             .required(false)
             .help("CSV file for the dataset table with postgres columns as headers");
    RepartitionCmd.configurePartitionNumber(subparser);
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Initialising database %s on %s.\n", cfg.db.database, cfg.db.host);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace ns, WsServerConfig cfg) throws Exception {
    int partitions = RepartitionCmd.getPartitionConfig(ns);
    LOG.info("Starting database initialisation with {} default partitions and admin connection {}", partitions, cfg.adminDb);
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
      // optionally insert datasets if given
      String fn = ns.getString(ARG_DATASET);
      if (fn != null) {
        File f = new File(fn);
        if (!f.exists()){
          throw new IllegalArgumentException("CSV file " + f.getAbsolutePath() + " does not exist");
        }
        InitDbUtils.insertDatasets(InitDbUtils.toPgConnection(con), new FileInputStream(f));
      }
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

    // create managed & default partitions
    HikariConfig hikari = cfg.db.hikariConfig();
    try (HikariDataSource dataSource = new HikariDataSource(hikari)) {
      // configure single mybatis session factory
      final SqlSessionFactory factory = MybatisFactory.configure(dataSource, "init");
      // default partitions
      Partitioner.createDefaultPartitions(factory, partitions);
      InitDbUtils.updateDatasetKeyConstraints(factory, cfg.db.minExternalDatasetKey);
      // add project partitions & dataset key constraints
      InitDbUtils.createNonDefaultPartitions(factory);
    }
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
