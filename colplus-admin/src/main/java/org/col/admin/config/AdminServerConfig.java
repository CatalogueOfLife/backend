package org.col.admin.config;

import io.dropwizard.client.JerseyClientConfiguration;
import org.col.dw.PgAppConfig;

public class AdminServerConfig extends PgAppConfig {

  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();
  public GbifConfig gbif = new GbifConfig();
  public JerseyClientConfiguration client = new JerseyClientConfiguration();
  public AnystyleConfig anystyle = new AnystyleConfig();

  /**
   * The application context for the IndexServlet which renders the
   * operational menu index.
   * Defaults to monitor e.g. http://admin.col.plus/monitor/
   */
  public String monitorPath = "monitor";

}
