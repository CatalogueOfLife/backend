package org.col.commands.config;

import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import org.col.db.PgConfig;

public class CliConfig extends Configuration {

  public PgConfig db = new PgConfig();
  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();
  public GbifConfig gbif = new GbifConfig();
  public JerseyClientConfiguration client = new JerseyClientConfiguration();

}
