package life.catalogue.concurrent;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.google.common.base.Preconditions;

import life.catalogue.api.model.DatasetExport;
import life.catalogue.api.model.JobResult;

import java.io.File;
import java.net.URI;
import java.util.UUID;

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

  public File downloadFile(UUID key) {
    return new File(downloadDir, JobResult.downloadFilePath(key));
  }

  /**
   * @return the final URI that holds the download archive file.
   */
  public URI downloadURI(UUID key) {
    return downloadURI.resolve(JobResult.downloadFilePath(key));
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return downloadDir.mkdirs();
  }

}
