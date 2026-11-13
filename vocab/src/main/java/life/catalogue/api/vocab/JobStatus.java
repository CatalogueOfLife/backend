package life.catalogue.api.vocab;

public enum JobStatus implements Described {

  WAITING("Job is queued."),

  BLOCKED("Job is blocked by another job."),

  RUNNING("Job is currently being executed."),

  FINISHED("Successfully completed the job."),

  CANCELED("Manually aborted or canceled by the system, e.g. if the system was shut down."),

  FAILED("Job failed due to unexpected errors.");

  private final String description;

  JobStatus(String description) {
    this.description = description;
  }

  @Override
  public String getDescription() {
    return description;
  }

  /**
   * @return true if the job has ended
   */
  public boolean isDone() {
    return this != WAITING && this != BLOCKED && this != RUNNING;
  }

  /**
   * @return true if the job is currently being executed
   */
  public boolean isRunning() {
    return this == RUNNING;
  }

  /**
   * @return true if the job still waits for execution
   */
  public boolean isQueued() {
    return this == WAITING || this == BLOCKED;
  }
}
