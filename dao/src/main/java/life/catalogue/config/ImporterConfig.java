package life.catalogue.config;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 *
 */
@SuppressWarnings("PublicField")
public class ImporterConfig {

  @Min(1)
  public int batchSize = 10000;
  
  /**
   * Number of parallel imports to allow simultanously
   */
  @Min(1)
  public int threads = 1;
  
  /**
   * Max size of queued import jobs before rejecting
   */
  @Min(100)
  public int maxQueue = 1000;
  


  /**
   * Github API access token to use when downloading data from github.com URLs.
   */
  public String githubToken;
  public String githubTokenGeoff;
  /**
   * Github shared secret to encrypt webhook messages
   */
  @Nullable
  public String githubHookSecret;

  /**
   * Number of seconds to wait after an import has finished before the job is done.
   * This is really for testing only to avoid import jobs to complete before the assertions have run.
   * Keep the default of zero for production environments!
   */
  public int wait = 0;

  @Valid
  @NotNull
  public ContinuousImportConfig continuous = new ContinuousImportConfig();

  /**
   * Map of GBIF publisher keys to short alias prefixed to be used in combination with the dataset key when a dataset is created.
   */
  public Map<UUID, String> publisherAlias = new HashMap<>();

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return false;
  }
}
