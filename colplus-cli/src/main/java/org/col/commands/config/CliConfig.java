package org.col.commands.config;

import io.dropwizard.Configuration;
import org.col.db.PgConfig;

public class CliConfig extends Configuration {

  public PgConfig db = new PgConfig();
  public NeoConfig neo = new NeoConfig();
  public ImporterConfig importer = new ImporterConfig();

}
