package life.catalogue.config;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
   * Number of seconds to wait after an import has finished before the job is done.
   * This is really for testing only to avoid import jobs to complete before the assertions have run.
   * Keep the default of zero for production environments!
   */
  public int wait = 0;

  @Valid
  @NotNull
  public ContinuousImportConfig continuous = new ContinuousImportConfig();

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return false;
  }
}
