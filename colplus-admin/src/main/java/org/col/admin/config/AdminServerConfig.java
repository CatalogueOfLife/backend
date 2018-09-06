package org.col.admin.config;

import java.io.File;

import javax.validation.constraints.Pattern;

import io.dropwizard.client.JerseyClientConfiguration;
import org.col.dw.PgAppConfig;
import org.col.es.EsConfig;

public class AdminServerConfig extends PgAppConfig {

  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();
  public GbifConfig gbif = new GbifConfig();
  public EsConfig es = new EsConfig();
  public JerseyClientConfiguration client = new JerseyClientConfiguration();

  /**
   * The application context for the IndexServlet which renders the operational menu index. Has to
   * be empty or start with a slash. Defaults to monitor e.g. http://admin.col.plus/monitor/
   */
  @Pattern(regexp = "^/.*")
  public String monitorPath = "/monitor";

  /**
   * Names index kvp file to persist map on disk. If empty will use a volatile memory index.
   */
  public File namesIndexFile;

}
