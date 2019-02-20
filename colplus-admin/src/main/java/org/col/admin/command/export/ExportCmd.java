package org.col.admin.command.export;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.NotImplementedException;
import org.col.admin.config.AdminServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to export a dataset as a ColDP archive.
 */
public class ExportCmd extends ConfiguredCommand<AdminServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(ExportCmd.class);
  private static final String SCRIPT = "org/col/db/colac-export.sql";
  
  public ExportCmd() {
    super("export", "Export dataset as ColDP");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("-d", "--datasetKey")
        .dest("datasetKey")
        .type(Integer.class)
        .required(true)
        .help("Key of dataset to export");
  }
  
  @Override
  protected void run(Bootstrap<AdminServerConfig> bootstrap, Namespace namespace, AdminServerConfig cfg) throws Exception {
    final int datasetKey = namespace.getInt("datasetKey");
    System.out.format("Exporting dataset %s from %s on %s.\n", datasetKey, cfg.db.database, cfg.db.host);
    
    try (Connection con = cfg.db.connect()) {
      throw new NotImplementedException("Not yet implemented");
    }
  }
  
  public static void executeSQL(Connection con, String sql, Map<String, String> params) throws SQLException {
    sql = sql.replaceAll("\r\n", "\n");
    try (PreparedStatement pst = con.prepareStatement(sql)) {
      //pst.setEscapeProcessing(true);
      //pst.setString();
      pst.execute(sql);
    }
  }

}
