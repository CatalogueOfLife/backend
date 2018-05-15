package org.col.admin.command.initdb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
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
  private static final URI COL_DATASETS_URI = URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/ACEF/datasets.sql");

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

  private void execute(AdminServerConfig cfg) throws Exception {
    try (Connection con = cfg.db.connect()) {
      LOG.info("Starting database initialisation");
      ScriptRunner runner = new ScriptRunner(con);
      runner.setSendFullScript(true);
      // run sql files
      exec(PgConfig.SCHEMA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.SCHEMA_FILE));
      // add COL GSDs
      try (Reader datasets = new InputStreamReader(COL_DATASETS_URI.toURL().openStream(), StandardCharsets.UTF_8)) {
        exec(COL_DATASETS_URI.toString(), runner, con, datasets);
      }
      // add GBIF Backbone datasets
      exec("gbif.sql", runner, con, Resources.getResourceAsReader("org/col/db/gbif.sql"));
    }
  }

  private void exec(String name, ScriptRunner runner, Connection con, Reader reader) throws IOException, SQLException {
    try {
      LOG.info("Executing {}", name);
      System.out.println("Execute " + name);
      runner.runScript(reader);
      con.commit();
      LOG.info("Executed {}", name);
    } catch (Exception e) {
      throw new IllegalStateException("Fail to execute sql file: " + name, e);
    }
  }
}
