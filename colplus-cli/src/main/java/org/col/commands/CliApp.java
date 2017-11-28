package org.col.commands;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.col.commands.config.CliConfig;
import org.col.commands.gbifsync.GbifSyncCmd;
import org.col.commands.hello.HelloCmd;
import org.col.commands.importer.ImporterCmd;
import org.col.commands.initdb.InitDbCmd;

public class CliApp extends Application<CliConfig> {

  public static void main(final String[] args) throws Exception {
    new CliApp().run(args);
  }

  @Override
  public String getName() {
    return "Clearinghouse";
  }

  @Override
  public void initialize(final Bootstrap<CliConfig> bootstrap) {
    // commands
    bootstrap.addCommand(new HelloCmd());
    bootstrap.addCommand(new InitDbCmd());
    bootstrap.addCommand(new ImporterCmd());
    bootstrap.addCommand(new GbifSyncCmd());
  }

  @Override
  public void run(final CliConfig config, final Environment environment) {
    environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

}
