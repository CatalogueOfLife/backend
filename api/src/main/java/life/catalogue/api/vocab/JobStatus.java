package life.catalogue.api.vocab;

public enum JobStatus {

  /**
   * Job is queued.
   */
  WAITING,

  /**
   * Job is blocked by another job.
   */
  BLOCKED,

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

  /**
   * @return true if the job has ended
   */
  public boolean isDone() {
    return this != WAITING && this != BLOCKED && this != RUNNING;
  }
}
