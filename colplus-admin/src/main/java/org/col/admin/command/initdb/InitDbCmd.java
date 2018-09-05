package org.col.admin.command.initdb;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.col.admin.config.AdminServerConfig;
import org.col.db.PgConfig;
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
      st.execute("DROP DATABASE IF EXISTS " + cfg.db.database);

      LOG.info("Create new database {}", cfg.db.database);
      st.execute("CREATE DATABASE " + cfg.db.database + " WITH OWNER " + cfg.db.user);
      //con.commit();
    }

    try (Connection con = cfg.db.connect()) {
      ScriptRunner runner = PgConfig.scriptRunner(con);
      // run sql schema
      exec(PgConfig.SCHEMA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.SCHEMA_FILE));
      // add common data
      exec(PgConfig.DATA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.DATA_FILE));
      // add COL GSDs
      try (Reader datasets = new InputStreamReader(PgConfig.COL_DATASETS_URI.toURL().openStream(), StandardCharsets.UTF_8)) {
        exec(PgConfig.COL_DATASETS_URI.toString(), runner, con, datasets);
      }
      // add GBIF Backbone datasets
      exec(PgConfig.GBIF_DATASETS_FILE, runner, con, Resources.getResourceAsReader(PgConfig.GBIF_DATASETS_FILE));
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
