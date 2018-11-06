package org.col.admin.config;

import java.io.File;

import org.col.db.PgDbConfig;
import org.col.dw.PgAppConfig;
import org.col.es.EsConfig;

public class AdminServerConfig extends PgAppConfig {

  public PgDbConfig adminDb = new PgDbConfig();
  public NormalizerConfig normalizer = new NormalizerConfig();
  public ImporterConfig importer = new ImporterConfig();
  public GbifConfig gbif = new GbifConfig();
  public EsConfig es = new EsConfig();

  /**
   * Names index kvp file to persist map on disk. If empty will use a volatile memory index.
   */
  public File namesIndexFile;

}
