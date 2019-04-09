package org.col;

import java.io.File;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import org.col.config.GbifConfig;
import org.col.config.ImporterConfig;
import org.col.config.NormalizerConfig;
import org.col.db.PgConfig;
import org.col.db.PgDbConfig;
import org.col.dw.auth.AuthenticationProviderFactory;
import org.col.dw.cors.CorsBundleConfiguration;
import org.col.dw.cors.CorsConfiguration;
import org.col.es.EsConfig;
import org.col.img.ImgConfig;


public class WsServerConfig extends Configuration implements CorsBundleConfiguration {
  
  @Valid
  @NotNull
  public PgConfig db = new PgConfig();
  
  @Valid
  @NotNull
  public EsConfig es = new EsConfig();
  
  @Valid
  @NotNull
  public PgDbConfig adminDb = new PgDbConfig();
  
  @Valid
  @NotNull
  public AuthenticationProviderFactory auth;
  
  /**
   * Json Web Token used to trust in externally authenticated users.
   */
  @NotNull
  public String jwtKey = "bhc564c76VT-d/722mc";

  @Valid
  @NotNull
  public GbifConfig gbif = new GbifConfig();
  
  @Valid
  @NotNull
  public NormalizerConfig normalizer = new NormalizerConfig();

  @Valid
  @NotNull
  public ImporterConfig importer = new ImporterConfig();
  
  @Valid
  @NotNull
  public CorsConfiguration cors = new CorsConfiguration();
  
  @Valid
  @NotNull
  public JerseyClientConfiguration client = new JerseyClientConfiguration();
  
  @Valid
  @NotNull
  public ImgConfig img = new ImgConfig();
  
  /**
   * Names index kvp file to persist map on disk. If empty will use a volatile memory index.
   */
  public File namesIndexFile;
  
  /**
   * Directory to store text tree and name index lists for each dataset and sector import attempt
   */
  @NotNull
  public File textTreeRepo = new File("/tmp/trees");

  /**
   * Directory to store export archives
   */
  @NotNull
  public File downloadDir = new File("/tmp");
  
  @NotNull
  public String raml = "https://sp2000.github.io/colplus/api/api.html";
  
  
  @Override
  @JsonIgnore
  public CorsConfiguration getCorsConfiguration() {
    return cors;
  }
}
