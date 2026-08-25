package life.catalogue.concurrent;

import life.catalogue.api.model.JobResult;
import life.catalogue.api.vocab.JobLane;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.common.base.Preconditions;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Background job configuration for processing of asynchroneous tasks.
 */
public class JobConfig {

  public static JobConfig withThreads(int threads) {
    Preconditions.checkArgument(threads >= 1, "At least one thread must be configured");
    JobConfig cfg = new JobConfig();
    cfg.threads = threads;
    return cfg;
  }

  /**
   * Directory to store job logs.
   * Should be the same as in JobAppenderFactory !!!
   */
  @NotNull
  public File logDir = new File("/tmp/log/jobs");

  /**
   * Directory to store download files
   */
  @NotNull
  public File downloadDir = new File("/tmp/jobs");

  /**
   * The URI for the directory containing job results.
   * Warning! The URI MUST end with a slash or otherwise resolved job URIs will be wrong!
   */
  @NotNull
  public URI downloadURI = URI.create("https://download.checklistbank.org/job/");

  /**
   * Maximum number of background job to run simultaneously.
   * Defines the pool size of the executors default lane.
   */
  @Min(1)
  public int threads = 1;

  /**
   * Maximum amount of jobs that can be queued in the default lane before they are rejected.
   */
  @Min(1)
  public int queue = 1000;

  /**
   * Maximum number of dataset imports to run simultaneously.
   */
  @Min(1)
  public int importThreads = 1;

  /**
   * Maximum amount of dataset imports that can be queued before they are rejected.
   */
  @Min(1)
  public int importQueue = 1000;

  /**
   * Maximum number of sector syncs to run simultaneously.
   * Syncs of the same project are always serialized, parallelism only happens across projects.
   */
  @Min(1)
  public int syncThreads = 1;

  /**
   * Maximum amount of sector syncs that can be queued before they are rejected.
   */
  @Min(1)
  public int syncQueue = 1000;

  public int threads(JobLane lane) {
    switch (lane) {
      case IMPORT: return importThreads;
      case SYNC: return syncThreads;
      default: return threads;
    }
  }

  public int queueSize(JobLane lane) {
    switch (lane) {
      case IMPORT: return importQueue;
      case SYNC: return syncQueue;
      default: return queue;
    }
  }

  /**
   * Maximum amount of jobs that a user can run or queue for a specific job class
   * Keys should be just the simple names of java job classes.
   */
  public Map<String, Integer> userLimit = new HashMap<>();

  /**
   * Days after which a finished job record is removed from the job table by the periodic cleanup.
   * Only ever applies to jobs no metrics table refers to - imports, sector syncs and exports keep their
   * job row for as long as their own record lives, since that row carries their status, step and error.
   */
  @Min(1)
  public int retentionDays = 90;

  /**
   * Per job class overrides of retentionDays, keyed by the simple java class name, matched case insensitively.
   */
  public Map<String, Integer> retentionDaysByClass = new HashMap<>();

  /**
   * Number of most recent jobs to keep per job class no matter how old they are, so a class that last ran
   * long ago can still be inspected.
   */
  @Min(0)
  public int retentionKeepPerClass = 10;

  /**
   * Maximum number of job records deleted in one statement. The cleanup loops until it is done, so this
   * only bounds how long a single delete transaction runs.
   */
  @Min(1)
  public int retentionBatchSize = 10_000;

  public String onErrorTo;

  public String onErrorFrom;

  public File downloadFile(UUID key) {
    return new File(downloadDir, JobResult.downloadFilePath(key));
  }

  /**
   * @return the final URI that holds the download archive file.
   */
  public URI downloadURI(UUID key) {
    return downloadURI.resolve(JobResult.downloadFilePath(key));
  }
  public URI logURI(UUID key) {
    return downloadURI.resolve(JobResult.downloadLogFilePath(key));
  }

  public static File jobLog(File directory, String key) {
    return new File(directory, "job-" + key + ".log.gz");
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return downloadDir.mkdirs();
  }

}
