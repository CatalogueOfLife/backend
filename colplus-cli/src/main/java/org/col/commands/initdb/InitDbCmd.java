package org.col.commands.initdb;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
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
  private static final int DELAY_IN_SECONDS = 10;
  private static final URI COL_DATASETS_URI = URI.create("https://raw.githubusercontent.com/Sp2000/colplus-repo/master/AC2017/datasets.sql");
  public InitDbCmd() {
    super("initdb", "Initialises a new database schema");
  }


  @Override
  protected void run(Bootstrap<CliConfig> bootstrap, Namespace namespace, CliConfig cfg) throws Exception {
    System.out.format("Initialising database %s on %s.\n", cfg.db.database, cfg.db.host);
    System.out.format("You have %s seconds to abort if you did not intend to do so !!!\n", DELAY_IN_SECONDS);
    TimeUnit.SECONDS.sleep(DELAY_IN_SECONDS);

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
