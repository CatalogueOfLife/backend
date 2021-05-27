package life.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import life.catalogue.api.model.DatasetExport;
import life.catalogue.common.io.Resources;
import life.catalogue.concurrent.JobConfig;
import life.catalogue.config.*;
import life.catalogue.db.PgConfig;
import life.catalogue.db.PgDbConfig;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.dw.auth.AuthenticationProviderFactory;
import life.catalogue.dw.cors.CorsBundleConfiguration;
import life.catalogue.dw.cors.CorsConfiguration;
import life.catalogue.dw.mail.MailBundleConfig;
import life.catalogue.dw.mail.MailConfig;
import life.catalogue.dw.metrics.GangliaBundleConfiguration;
import life.catalogue.dw.metrics.GangliaConfiguration;
import life.catalogue.es.EsConfig;
import life.catalogue.img.ImgConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.net.URI;
import java.util.Properties;
import java.util.UUID;


public class WsServerConfig extends Configuration implements CorsBundleConfiguration, GangliaBundleConfiguration, MailBundleConfig {
  private static final Logger LOG = LoggerFactory.getLogger(WsServerConfig.class);
  
  public Properties version;
  
  @Valid
  @NotNull
  public PgConfig db = new PgConfig();
  
  @Valid
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
  public DoiConfig doi;

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
  public GangliaConfiguration ganglia = new GangliaConfiguration();

  @Valid
  @NotNull
  // https://www.dropwizard.io/en/latest/manual/configuration.html#man-configuration-clients-http
  public JerseyClientConfiguration client = new JerseyClientConfiguration();

  @Valid
  @NotNull
  public ImgConfig img = new ImgConfig();

  @Valid
  @NotNull
  public ReleaseConfig release = new ReleaseConfig();

  @Valid
  @NotNull
  public MailConfig mail;

  @Valid
  public JobConfig job = new JobConfig();

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
  public File exportDir = new File("/tmp/exports");

  @NotNull
  public String exportCss = "https://gitcdn.link/repo/CatalogueOfLife/backend/master/webservice/src/main/resources/exporter/html/catalogue.css";

  @NotNull
  public URI downloadURI = URI.create("https://download.catalogueoflife.org");

  @NotNull
  public URI portalURI = URI.create("https://www.catalogueoflife.org");

  @NotNull
  public URI clbURI = URI.create("https://data.catalogueoflife.org");

  public URI apiURI = URI.create("https://api.catalogueoflife.org");

  /**
   * Optional URI to a TSV file that contains a mapping of legacy COL IDs to new name usage IDs.
   * First column must be the legacy ID, second column the new name usage ID.
   */
  public URI legacyIdMapURI;

  /**
   * File to persist legacy id map on disk. If empty will use a volatile memory map.
   */
  public File legacyIdMapFile;


  @Override
  @JsonIgnore
  public CorsConfiguration getCorsConfiguration() {
    return cors;
  }

  @Override
  @JsonIgnore
  public GangliaConfiguration getGangliaConfiguration() {
    return ganglia;
  }

  @Override
  public MailConfig getMailConfig() {
    return mail;
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
    boolean created = exportDir.mkdirs();
    created = metricsRepo.mkdirs() || created;
    created = normalizer.mkdirs() || created;
    created = importer.mkdirs() || created;
    created = release.mkdirs() || created;
    return created;
  }

  public String versionString() {
    if (version != null) {
      return version.getProperty("git.commit.id.abbrev") + "  " + version.getProperty("git.commit.time");
    }
    return null;
  }

  public File downloadFile(UUID key) {
    return new File(exportDir, DatasetExport.downloadFilePath(key));
  }


}
