package life.catalogue.common.concurrent;

public enum JobStatus {

  /**
   * Job is queued.
   */
  WAITING,

  /**
   * Job is currently being executed.
   */
  RUNNING,

  /**
   * Successfully completed the job.
   */
  FINISHED,

  /**
   * Manually aborted or canceled by the system, e.g. if the system was shut down.
   */
  CANCELED,

  /**
   * Job failed due to unexpected errors.
   */
  FAILED;
}
