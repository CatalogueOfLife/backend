package org.col.config;

import io.dropwizard.Configuration;

public class ColAppConfig extends Configuration {

  public PgConfig db = new PgConfig();
  public NeoConfig neo = new NeoConfig();
  public ImporterConfig importer = new ImporterConfig();

}
