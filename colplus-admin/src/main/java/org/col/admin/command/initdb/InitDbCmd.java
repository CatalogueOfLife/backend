package org.col.admin.command.initdb;

import java.io.Reader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

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
import org.col.api.model.Name;
import org.col.api.model.Taxon;
import org.col.api.vocab.Datasets;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Users;
import org.col.db.MybatisFactory;
import org.col.db.PgConfig;
import org.col.db.mapper.DatasetPartitionMapper;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.TaxonMapper;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
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
      // add known datasets
      exec(PgConfig.DATASETS_FILE, runner, con, Resources.getResourceAsReader(PgConfig.DATASETS_FILE));
    }
    
    // add col & names index partitions
    setupStandardPartitions(cfg.db);
  }

  private static void setupStandardPartitions(PgConfig cfg) {
    HikariConfig hikari = cfg.hikariConfig();
    try (HikariDataSource dataSource = new HikariDataSource(hikari)) {
      // configure single mybatis session factory
      SqlSessionFactory factory = MybatisFactory.configure(dataSource, "init");
      SqlSession session = factory.openSession();
      setupStandardPartitions(session);
      session.commit();
      session.close();
    }
  }

  public static void setupStandardPartitions(SqlSession session) {
    final Name nBiota = new Name();
    nBiota.setId("biota");
    nBiota.setScientificName("Biota");
    nBiota.setRank(Rank.SUPERKINGDOM);
    nBiota.setHomotypicNameId(nBiota.getId());
    nBiota.setOrigin(Origin.SOURCE);
    nBiota.setType(NameType.INFORMAL);
    nBiota.setCreatedBy(Users.DB_INIT);
    nBiota.setModifiedBy(Users.DB_INIT);

    final Taxon tBiota = new Taxon();
    tBiota.setId("root");
    tBiota.setName(nBiota);
    tBiota.setOrigin(Origin.SOURCE);
    tBiota.setCreatedBy(Users.DB_INIT);
    tBiota.setModifiedBy(Users.DB_INIT);

    DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
    NameMapper nm = session.getMapper(NameMapper.class);
    TaxonMapper tm = session.getMapper(TaxonMapper.class);
    for (int key : new int[]{Datasets.COL, Datasets.PCAT, Datasets.DRAFT_COL}) {
      LOG.info("Create catalogue partition {}", key);
      pm.delete(key);
      pm.create(key);
      pm.buildIndices(key);
      pm.attach(key);
      // add single Biota taxon
      nBiota.setDatasetKey(key);
      nm.create(nBiota);

      tBiota.setDatasetKey(key);
      tm.create(tBiota);
    }
  }
  
  private static void exec(String name, ScriptRunner runner, Connection con, Reader reader) {
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
