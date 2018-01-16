package org.col.config;

import io.dropwizard.client.JerseyClientConfiguration;
import org.col.PgAppConfig;

public class TaskServerConfig extends PgAppConfig {

  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();
  public GbifConfig gbif = new GbifConfig();
  public JerseyClientConfiguration client = new JerseyClientConfiguration();

}
