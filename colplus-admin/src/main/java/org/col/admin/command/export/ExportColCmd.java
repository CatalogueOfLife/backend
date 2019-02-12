package org.col.admin.command.export;

import java.sql.Connection;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.col.admin.command.initdb.InitDbCmd;
import org.col.admin.config.AdminServerConfig;
import org.col.db.PgConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to export the draft CoL in the assembly global CSV format.
 */
public class ExportColCmd extends ConfiguredCommand<AdminServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(ExportColCmd.class);
  private static final String SCRIPT = "org/col/db/colac-export.sql";
  
  public ExportColCmd() {
    super("col-export", "Exports CoL Draft");
  }
  
  @Override
  protected void run(Bootstrap<AdminServerConfig> bootstrap, Namespace namespace, AdminServerConfig cfg) throws Exception {
    System.out.format("Exporting draft CoL from %s on %s.\n", cfg.db.database, cfg.db.host);
  
    try (Connection con = cfg.db.connect()) {
      ScriptRunner runner = PgConfig.scriptRunner(con);
      // run export script
      InitDbCmd.exec(SCRIPT, runner, con, Resources.getResourceAsReader(SCRIPT));
    }
    System.out.println("Done !!!");
  }
  
}
