package life.catalogue.outbox;

import jakarta.validation.constraints.Min;

/**
 * Configuration of the {@link OutboxRunner}.
 */
public class OutboxConfig {

  /**
   * Milliseconds between two looks for due commands.
   */
  @Min(100)
  public int pollMillis = 2000;

  /**
   * Attempts after which a failing command is given up and marked as failed.
   */
  @Min(1)
  public int maxAttempts = 12;

  /**
   * Seconds to wait before retrying a command this server could not run, e.g. because the job executor was stopped.
   */
  @Min(1)
  public int retryLaterSeconds = 60;

  /**
   * Seconds a stopping runner waits for running commands to finish before it interrupts them.
   */
  @Min(0)
  public int stopGraceSeconds = 10;

  /**
   * Days a failed command is kept for inspection and manual retry.
   */
  @Min(1)
  public int failedRetentionDays = 30;

  /**
   * Minutes a pending command may be overdue before the health check reports it.
   */
  @Min(1)
  public int overdueMinutes = 15;

  /**
   * Hours a command may wait since it was requested before the health check reports it as stuck. A command that is
   * put off again and again, e.g. the deletion of a sector that is always busy, is never overdue.
   */
  @Min(1)
  public int stuckHours = 24;
}
