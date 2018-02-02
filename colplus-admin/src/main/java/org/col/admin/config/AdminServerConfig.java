package org.col.admin.config;

import io.dropwizard.client.JerseyClientConfiguration;
import org.col.dw.PgAppConfig;

public class AdminServerConfig extends PgAppConfig {

  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();
  public GbifConfig gbif = new GbifConfig();
  public JerseyClientConfiguration client = new JerseyClientConfiguration();

}
