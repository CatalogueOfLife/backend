package org.col.commands.initdb;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.col.commands.config.CliConfig;
import org.col.db.PgConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Basic task to showcase hello world
 */
public class InitDbCmd extends ConfiguredCommand<CliConfig> {
  private static final URI COL_DATASETS_URI = URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/AC2017/datasets.sql");

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
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    final int prompt = namespace.getInt("prompt");
    if (prompt > 0) {
      System.out.format("Initialising database %s on %s.\n", cfg.db.database, cfg.db.host);
      System.out.format("You have %s seconds to abort if you did not intend to do so !!!\n", prompt);
      TimeUnit.SECONDS.sleep(prompt);
    }

    execute(cfg);
  }

  private void execute(CliConfig cfg) throws Exception {
    try (Connection con = cfg.db.connect()) {
      System.out.println("Starting database initialisation");
      ScriptRunner runner = new ScriptRunner(con);
      // run sql files
      exec(PgConfig.SCHEMA_FILE, runner, con, Resources.getResourceAsReader(PgConfig.SCHEMA_FILE));

      try (BufferedReader datasets = new BufferedReader(new InputStreamReader(COL_DATASETS_URI.toURL().openStream()))) {
        exec(COL_DATASETS_URI.toString(), runner, con, datasets);
      }
    }
  }

  private void exec(String name, ScriptRunner runner, Connection con, Reader reader) throws IOException, SQLException {
    try {
      System.out.println("Run ");
      runner.runScript(reader);
      con.commit();
    } catch (Exception e) {
      throw new IllegalStateException("Fail to execute sql file: " + name, e);
    }
  }
}
