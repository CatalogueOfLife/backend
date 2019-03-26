package org.col.config;

import java.io.File;
import javax.validation.constraints.NotNull;

import org.col.db.PgDbConfig;
import org.col.dw.PgAppConfig;
import org.col.es.EsConfig;


public class WsServerConfig extends PgAppConfig {
  
  public PgDbConfig adminDb = new PgDbConfig();
  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();
  public GbifConfig gbif = new GbifConfig();
  public EsConfig es = new EsConfig();
  
  /**
   * Names index kvp file to persist map on disk. If empty will use a volatile memory index.
   */
  public File namesIndexFile;
  
  /**
   * Directory to store export archives
   */
  @NotNull
  public File downloadDir = new File("/tmp");
  
  @NotNull
  public String raml = "https://sp2000.github.io/colplus/api/api.html";
  
}
