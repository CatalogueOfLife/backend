package life.catalogue;

import life.catalogue.common.io.Resources;
import life.catalogue.db.PgConfig;
import life.catalogue.matching.MatchingConfig;
import life.catalogue.matching.nidx.NamesIndexConfig;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.core.Configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;


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
