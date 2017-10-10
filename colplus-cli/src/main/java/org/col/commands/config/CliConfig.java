package org.col.commands.config;

import io.dropwizard.Configuration;
import org.col.db.PgConfig;

public class CliConfig extends Configuration {

  public PgConfig db = new PgConfig();
  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();

}
