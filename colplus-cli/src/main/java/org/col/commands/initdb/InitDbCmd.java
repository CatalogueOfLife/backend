package org.col.commands.initdb;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.col.config.ColAppConfig;
import org.col.db.MybatisBundle;

import java.sql.Connection;
import java.util.concurrent.TimeUnit;

/**
 * Basic task to showcase hello world
 */
public class InitDbCmd extends ConfiguredCommand<ColAppConfig> {
  private static final int DELAY_IN_SECONDS = 10;
  public InitDbCmd() {
    super("initdb", "Initialises a new database schema");
  }

  @Override
  protected void run(Bootstrap<ColAppConfig> bootstrap, Namespace namespace, ColAppConfig cfg) throws Exception {
    System.out.format("Initialising database %s on %s.\n", cfg.db.database, cfg.db.host);
    System.out.format("You have %s seconds to abort if you did not intend to do so !!!\n", DELAY_IN_SECONDS);
    TimeUnit.SECONDS.sleep(DELAY_IN_SECONDS);

    try (Connection con = cfg.db.connect()) {

      System.out.println("Starting database initialisation");
      ScriptRunner runner = new ScriptRunner(con);
      try {
        runner.runScript(Resources.getResourceAsReader(MybatisBundle.SCHEMA_FILE));
        con.commit();

      } catch (Exception e) {
        throw new IllegalStateException("Fail to restore: " + MybatisBundle.SCHEMA_FILE, e);
      }
    }
  }
}
