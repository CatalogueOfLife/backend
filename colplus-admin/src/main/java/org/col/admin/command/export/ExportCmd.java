package org.col.admin.command.export;

import io.dropwizard.cli.ConfiguredCommand;
import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.col.admin.config.AdminServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to export a dataset as a ColDP archive.
 */
public class ExportCmd extends ConfiguredCommand<AdminServerConfig> {
  private static final Logger LOG = LoggerFactory.getLogger(ExportCmd.class);
  
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
  
    AcExporter exporter = new AcExporter(cfg);
    exporter.export(datasetKey);
  }
  
}
