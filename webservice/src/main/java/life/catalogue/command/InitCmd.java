package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.common.io.PathUtils;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.MybatisFactory;
import life.catalogue.db.PgConfig;
import life.catalogue.db.PgUtils;
import life.catalogue.es.EsClientFactory;
import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.Reader;
import java.sql.Connection;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command to initialise a new database schema and an elasticsearch index.
 */
public class InitCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(InitCmd.class);
  private static final String ARG_DATASET = "dataset";

  public InitCmd() {
    super("init", "Initialises a new database & search schema");
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
    LOG.info("Starting database & elasticsearch initialisation with {} default partitions and admin connection {}", partitions, cfg.adminDb);
    try (Connection con = cfg.db.connect(cfg.adminDb)) {
      PgUtils.createDatabase(con, cfg.db.database, cfg.db.user);
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


    try (var pool = cfg.db.pool()) {
      var factory = MybatisFactory.configure(pool, "test");
      // partition tables
      LOG.info("Create {} partitions", partitions);
      Partitioner.createPartitions(factory, partitions);
    }
    
    // cleanup names index
    if (cfg.namesIndex.file != null && cfg.namesIndex.file.exists()) {
      LOG.info("Clear names index at {}", cfg.namesIndex.file.getAbsolutePath());
      if (!cfg.namesIndex.file.delete()) {
        LOG.error("Unable to delete names index at {}", cfg.namesIndex.file.getAbsolutePath());
        throw new IllegalStateException("Unable to delete names index at " + cfg.namesIndex.file.getAbsolutePath());
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

    // create new ES index
    if (cfg.es != null) {
      final var index = cfg.es.nameUsage;
      final String indexAlias = cfg.es.nameUsage.name;
      final String indexToday = IndexCmd.indexNameToday();
      LOG.info("Create new elasticsearch index {} with alias {}", indexToday, indexAlias);
      try (RestClient client = new EsClientFactory(cfg.es).createClient()) {
        if (EsUtil.indexExists(client, index.name)) {
          EsUtil.deleteIndex(client, index); // alias
        }
        index.name = indexToday;
        if (EsUtil.indexExists(client, index.name)) {
          EsUtil.deleteIndex(client, index); // today - just in case we use the command several times a day
        }
        EsUtil.createIndex(client, EsNameUsage.class, index);
        LOG.info("Bind alias {} to new search index {}", indexAlias, index.name);
        EsUtil.createAlias(client, index.name, indexAlias);
        index.name = indexAlias;
      }
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
