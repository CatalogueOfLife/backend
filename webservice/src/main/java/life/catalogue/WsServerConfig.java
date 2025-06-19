package life.catalogue;

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
import life.catalogue.es.EsConfig;
import life.catalogue.event.BrokerConfig;
import life.catalogue.exporter.ExporterConfig;
import life.catalogue.feedback.GithubConfig;
import life.catalogue.img.ImgConfig;
import life.catalogue.matching.DockerConfig;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;
import java.util.Properties;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


public class WsServerConfig extends Configuration implements ExporterConfig, CorsBundleConfiguration, MailBundleConfig {
  private static final Logger LOG = LoggerFactory.getLogger(WsServerConfig.class);
  
  public Properties version;
  
  @Valid
  @NotNull
  public PgConfig db = new PgConfig();
  
  @Valid
  public EsConfig es;

  @Valid
  public BrokerConfig broker = new BrokerConfig();

  @Valid
  public EsConfig analytics;

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
  public DockerConfig docker = new DockerConfig();

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
  public SyncManagerConfig syncs = new SyncManagerConfig();

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

  @Valid
  @NotNull
  public ReleaseConfig release = new ReleaseConfig();

  @Valid
  @NotNull
  public MailConfig mail;

  @Valid
  public JobConfig job = new JobConfig();

  @Valid
  public GithubConfig github;

  /**
   * The size of the in memory log collector queue
   */
  @Min(3)
  public int logCollectorSize = 1000;

  /**
   * The name parser timeout in milliseconds
   */
  @Min(100)
  public long parserTimeout = 5000;

  /**
   * The maximum allowed time in seconds for a unix diff to take before throwing a time out.
   */
  @Min(1)
  public int diffTimeout = 30;

  @Valid
  @NotNull
  public NamesIndexConfig namesIndex = new NamesIndexConfig();

  /**
   * Usage cache mapdb file to persist map on disk. If empty will use a volatile memory index.
   */
  @NotNull
  public File usageCacheFile = new File("/tmp/usage-cache");

  /**
   * Directory to store text tree, name index lists and other metrics for each dataset and sector import attempt
   * on disc.
   */
  @NotNull
  public File metricsRepo = new File("/tmp/metrics");

  @NotNull
  public URI clbURI = URI.create("https://www.checklistbank.org");

  @NotNull
  public URI portalURI = URI.create("https://www.catalogueoflife.org");

  public URI apiURI;

  /**
   * JSON status file to be updated for maintenance mode.
   * Should be served as a static file from https://download.checklistbank.org/.status.json
   */
  @NotNull
  public File statusFile = new File("/tmp/.status.json");

  /**
   * The directory where the templates for the dynamic data pages of the life.catalogue.portal are stored.
   * See PortalPageRenderer.
   */
  @NotNull
  public File portalTemplateDir = new File("/tmp/col/life.catalogue.portal-templates");

  /**
   * Optional URI to a TSV file that contains a mapping of legacy COL IDs to new name usage IDs.
   * First column must be the legacy ID, second column the new name usage ID.
   */
  @Nullable
  public URI legacyIdMapURI;

  /**
   * File to persist legacy id map on disk. If empty will use a volatile memory map.
   */
  public File legacyIdMapFile;

  @NotNull
  public String support = "support@catalogueoflife.org";

  /**
   * Optional sunset value for the deprecation header.
   * See https://datatracker.ietf.org/doc/draft-ietf-httpapi-deprecation-header/
   */
  public LocalDate sunset;

  /**
   * Delay in milliseconds to all requests to the legacy API.
   */
  @Min(0)
  public int legacyDelay = 0;

  @Override
  @JsonIgnore
  public CorsConfiguration getCorsConfiguration() {
    return cors;
  }

  @Override
  @JsonIgnore
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
    boolean created = job.mkdirs();
    created = metricsRepo.mkdirs() || created;
    created = normalizer.mkdirs() || created;
    created = importer.mkdirs() || created;
    created = release.mkdirs() || created;
    return created;
  }

  public void logDirectories() {
    LOG.info("Use archive directory {}", normalizer.archiveDir);
    LOG.info("Use scratch directory {}", normalizer.scratchDir);
    LOG.info("Use metrics directory {}", metricsRepo);
    LOG.info("Use download directory {}", job.downloadDir);
    LOG.info("Use release reports directory {}", release.reportDir);
    LOG.info("Use release downloads directory {}", release.colDownloadDir);
  }

  public String versionString() {
    if (version != null) {
      String datetime = version.getProperty("git.commit.time").substring(0, 10);
      return version.getProperty("git.commit.id.abbrev") + " " + datetime;
    }
    return null;
  }

  @Override
  public URI getApiUri() {
    return apiURI;
  }

  @Override
  public JobConfig getJob() {
    return job;
  }

  @Override
  public ImgConfig getImgConfig() {
    return img;
  }

  @Override
  public NormalizerConfig getNormalizerConfig() {
    return normalizer;
  }
}
