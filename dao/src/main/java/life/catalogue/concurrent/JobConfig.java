package life.catalogue.concurrent;

import javax.validation.constraints.Min;

import com.google.common.base.Preconditions;

/**
 * Background job configuration for processing of asynchroneous tasks.
 */
public class JobConfig {

  /**
   * Maximum number of background job to run simultaneously.
   * Defines the pool size of the executor.
   */
  @Min(1)
  public int threads = 1;

  /**
   * Maximum amount of jobs that can be queued before they are rejected.
   */
  @Min(1)
  public int queue = 1000;

  public String onErrorTo;

  public String onErrorFrom;

  public static JobConfig withThreads(int threads) {
    Preconditions.checkArgument(threads >= 1, "At least one thread must be configured");
    JobConfig cfg = new JobConfig();
    cfg.threads = threads;
    return cfg;
  }
}
