package org.col.admin.config;

import javax.validation.constraints.Min;

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
   * Duration in minutes the continous import scheduler will fall to sleep if imports are running already.
   * Zero or negative values will turn off continuous importing.
   */
  @Min(0)
  public int continousImportPolling = 0;
  
  /**
   * Github API access token to use when downloading data from github.com URLs.
   */
  public String githubToken;
  
  /**
   * Number of seconds to wait after an import has finished before the job is done.
   * This is really for testing only to avoid import jobs to complete before the assertions have run.
   * Keep the default of zero for production environments!
   */
  public int wait = 0;
  
}
