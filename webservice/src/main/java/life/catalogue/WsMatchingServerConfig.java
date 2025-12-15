package life.catalogue;

import life.catalogue.common.io.Resources;
import life.catalogue.config.MatchingConfig;
import life.catalogue.db.PgConfig;
import life.catalogue.dw.cors.CorsBundleConfiguration;
import life.catalogue.dw.cors.CorsConfiguration;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.util.Properties;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


public class WsMatchingServerConfig extends Configuration implements CorsBundleConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(WsMatchingServerConfig.class);

  public Properties version;

  @Valid
  @Nullable
  public PgConfig db;

  @Valid
  @NotNull
  public CorsConfiguration cors = new CorsConfiguration();

  /**
   * The name parser timeout in milliseconds
   */
  @Min(100)
  public long parserTimeout = 5000;

  @Valid
  @NotNull
  public NamesIndexConfig namesIndex = new NamesIndexConfig();

  /**
   * The dataset
   */
  @Min(1)
  public int matchingDatasetKey;

  @Valid
  @NotNull
  public MatchingConfig matching = new MatchingConfig();

  @Override
  @JsonIgnore
  public CorsConfiguration getCorsConfiguration() {
    return cors;
  }

  public WsMatchingServerConfig() {
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
    return matching.mkdirs();
  }

  public void logDirectories() {
    LOG.info("Use matcher storage directory {}", matching.storageDir);
    LOG.info("Use matcher upload directory {}", matching.uploadDir);
  }

  public String versionString() {
    if (version != null) {
      String datetime = version.getProperty("git.commit.time").substring(0, 10);
      return version.getProperty("git.commit.id.abbrev") + " " + datetime;
    }
    return null;
  }
}
