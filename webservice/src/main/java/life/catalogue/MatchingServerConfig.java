package life.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

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
import life.catalogue.dw.metrics.GangliaBundleConfiguration;
import life.catalogue.dw.metrics.GangliaConfiguration;
import life.catalogue.es.EsConfig;
import life.catalogue.exporter.ExporterConfig;
import life.catalogue.feedback.GithubConfig;
import life.catalogue.img.ImgConfig;
import life.catalogue.matching.DockerConfig;
import life.catalogue.matching.MatchingConfig;
import life.catalogue.matching.nidx.NamesIndexConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;
import java.util.Properties;


public class MatchingServerConfig extends Configuration {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingServerConfig.class);

  public Properties version;

  @Valid
  @NotNull
  public PgConfig db = new PgConfig();

  @Valid
  @NotNull
  public MatchingConfig matching = new MatchingConfig();

  /**
   * The name parser timeout in milliseconds
   */
  @Min(100)
  public long parserTimeout = 5000;

  @Valid
  @NotNull
  public NamesIndexConfig namesIndex = new NamesIndexConfig();

  public MatchingServerConfig() {
    try {
      version = new Properties();
      version.load(Resources.reader("version/git.properties"));
    } catch (Exception e) {
      LOG.warn("Failed to load versions properties: {}", e.getMessage());
      version = null;
    }
  }

  public String versionString() {
    if (version != null) {
      String datetime = version.getProperty("git.commit.time").substring(0, 10);
      return version.getProperty("git.commit.id.abbrev") + " " + datetime;
    }
    return null;
  }

}
