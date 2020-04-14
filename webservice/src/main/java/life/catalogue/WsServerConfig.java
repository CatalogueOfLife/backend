package life.catalogue;

import java.io.File;
import java.util.Properties;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import life.catalogue.common.io.Resources;
import life.catalogue.config.GbifConfig;
import life.catalogue.config.ImporterConfig;
import life.catalogue.config.NormalizerConfig;
import life.catalogue.db.PgConfig;
import life.catalogue.db.PgDbConfig;
import life.catalogue.dw.auth.AuthenticationProviderFactory;
import life.catalogue.dw.cors.CorsBundleConfiguration;
import life.catalogue.dw.cors.CorsConfiguration;
import life.catalogue.es.EsConfig;
import life.catalogue.img.ImgConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WsServerConfig extends Configuration implements CorsBundleConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(WsServerConfig.class);
  
  public Properties version;
  
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

  /**
   * Require a secure SSL connection when basic authentication is used.
   */
  public boolean requireSSL = false;

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
  // https://www.dropwizard.io/en/latest/manual/configuration.html#man-configuration-clients-http
  public JerseyClientConfiguration client = new JerseyClientConfiguration();
  
  @Valid
  @NotNull
  public ImgConfig img = new ImgConfig();
  
  /**
   * Names index kvp file to persist map on disk. If empty will use a volatile memory index.
   */
  public File namesIndexFile;
  
  /**
   * Directory to store text tree, name index lists and other metrics for each dataset and sector import attempt
   * on disc.
   */
  @NotNull
  public File metricsRepo = new File("/tmp/metrics");

  /**
   * Directory to store export archives
   */
  @NotNull
  public File downloadDir = new File("/tmp");
  
  @NotNull
  public String raml = "https://catalogueoflife.github.io/general/api/api.html";
  
  
  @Override
  @JsonIgnore
  public CorsConfiguration getCorsConfiguration() {
    return cors;
  }
  

  public WsServerConfig() {
    try {
      version = new Properties();
      version.load(Resources.reader("version/git.properties"));
    } catch (Exception e) {
      LOG.warn("Failed to load versions properties: {}", e.getMessage());
      version = null;
    }
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    boolean created = downloadDir.mkdirs();
    created = metricsRepo.mkdirs() || created;
    created = normalizer.mkdirs() || created;
    created = importer.mkdirs() || created;
    return created;
  }

  public String versionString() {
    if (version != null) {
      return version.getProperty("git.commit.id.abbrev") + "  " + version.getProperty("git.commit.time");
    }
    return null;
  }

}
