package org.col.admin.command.initdb;

import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.config.AdminServerConfig;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.Users;
import org.col.db.MybatisFactory;
import org.col.db.PgConfig;
import org.col.db.dao.DecisionRematcher;
import org.col.postgres.PgCopyUtils;
import org.col.db.mapper.DatasetPartitionMapper;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to initialise a new database schema.
 */
public class InitDbCmd extends ConfiguredCommand<AdminServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(InitDbCmd.class);
  
  public InitDbCmd() {
    super("initdb", "Initialises a new database schema");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("--prompt")
        .setDefault(10)
        .dest("prompt")
        .type(Integer.class)
        .required(false)
        .help("Waiting time in seconds for a user prompt to abort db initialisation. Use zero for no prompt");
  }
  
  @Override
  protected void run(Bootstrap<AdminServerConfig> bootstrap, Namespace namespace, AdminServerConfig cfg) throws Exception {
    final int prompt = namespace.getInt("prompt");
    if (prompt > 0) {
      System.out.format("Initialising database %s on %s.\n", cfg.db.database, cfg.db.host);
      System.out.format("You have %s seconds to abort if you did not intend to do so !!!\n", prompt);
      TimeUnit.SECONDS.sleep(prompt);
    }
    
    execute(cfg);
    System.out.println("Done !!!");
  }
  
  public static void execute(AdminServerConfig cfg) throws Exception {
    LOG.info("Starting database initialisation");
    try (Connection con = cfg.db.connect(cfg.adminDb);
         Statement st = con.createStatement()
    ) {
      LOG.info("Drop existing database {}", cfg.db.database);
      st.execute("DROP DATABASE IF EXISTS \"" + cfg.db.database + "\"");
      
      LOG.info("Create new database {}", cfg.db.database);
      st.execute("CREATE DATABASE  \"" + cfg.db.database + "\"" + " WITH OWNER " + cfg.db.user);
    }
    
    try (Connection con = cfg.db.connect()) {
      ScriptRunner runner = PgConfig.scriptRunner(con);
      // run sql schema
      exec(PgConfig.SCHEMA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.SCHEMA_FILE));
      // add common data
      exec(PgConfig.DATA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.DATA_FILE));
    }
  
    HikariConfig hikari = cfg.db.hikariConfig();
    try (HikariDataSource dataSource = new HikariDataSource(hikari)) {
      // configure single mybatis session factory
      final SqlSessionFactory factory = MybatisFactory.configure(dataSource, "init");
      
      // add col & names index partitions
      try (SqlSession session = factory.openSession()) {
        setupStandardPartitions(session);
        session.commit();
      }
      
      try (Connection con = cfg.db.connect()) {
        // add draft hierarchy
        loadDraftHierarchy(con);
  
        ScriptRunner runner = PgConfig.scriptRunner(con);
        // add known datasets
        exec(PgConfig.DATASETS_FILE, runner, con, Resources.getResourceAsReader(PgConfig.DATASETS_FILE));
        // add known sectors
        exec(PgConfig.SECTORS_FILE, runner, con, Resources.getResourceAsReader(PgConfig.SECTORS_FILE));
        // add known decisions
        exec(PgConfig.DECISIONS_FILE, runner, con, Resources.getResourceAsReader(PgConfig.DECISIONS_FILE));
      }
      
      // rematch sector targets
      rematchSectorTargets(factory);
    }
  }
  
  private static void rematchSectorTargets(SqlSessionFactory factory) {
    LOG.info("Rematch sector targets");
    try (SqlSession session = factory.openSession(true)) {
      new DecisionRematcher(session).matchBrokenSectorTargets();
    }
  }
  
  private static void loadDraftHierarchy(Connection con) throws Exception {
    PgConnection pgc = (PgConnection) con;
    PgCopyUtils.copy(pgc, "name_3", "/org/col/db/draft/name.csv", ImmutableMap.<String, Object>builder()
        .put("dataset_key", 3)
        .put("origin", 0)
        .put("created_by", Users.DB_INIT)
        .put("modified_by", Users.DB_INIT)
        .build());
    PgCopyUtils.copy(pgc, "taxon_3", "/org/col/db/draft/taxon.csv", ImmutableMap.<String, Object>builder()
        .put("dataset_key", 3)
        .put("origin", 0)
        .put("according_to", "CoL")
        //.put("according_to_date", Year.now().getValue())
        .put("created_by", Users.DB_INIT)
        .put("modified_by", Users.DB_INIT)
        .build());
  }
  
  public static void setupStandardPartitions(SqlSession session) {
    DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    for (int key : new int[]{Datasets.COL, Datasets.PCAT, Datasets.DRAFT_COL}) {
      LOG.info("Create catalogue partition {}", key);
      pm.delete(key);
      pm.create(key);
      pm.buildIndices(key);
      pm.attach(key);
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
