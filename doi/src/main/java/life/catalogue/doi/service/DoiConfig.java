package life.catalogue.doi.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import life.catalogue.api.model.DOI;

import com.google.common.base.MoreObjects;

import jakarta.validation.constraints.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * DataCite DOI configuration.
 * If no username and password is provided no DOI registration will take place.
 */
public class DoiConfig {

  @NotNull
  public String api = "https://api.test.datacite.org";

  @NotNull
  public String username;

  @NotNull
  public String password;

  /**
   * DOI prefix to be used for COL DOIs.
   * Defaults to the test system.
   */
  @NotNull
  public String prefix = "10.80631";

  /**
   * Seconds a DOI metadata update waits in the outbox before it is sent to DataCite,
   * so that several changes made shortly after each other end up in a single update.
   */
  @Min(2)
  @Max(24*60*60)  // 1 day
  public int waitPeriod = 5*60; // 5 minutes

  /**
   * If true validates datacite metadata before sending it to the API.
   */
  public boolean validateMetadata = true;

  public boolean hasCredentials() {
    return username != null && password != null;
  }

  public DOI datasetDOI(int datasetKey) {
    return DOI.dataset(prefix, datasetKey);
  }

  public DOI datasetVersionDOI(int datasetKey, int attempt) {
    return DOI.datasetVersion(prefix, datasetKey, attempt);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("api", api)
      .add("prefix", prefix)
      .add("user", username)
      .toString();
  }
}
