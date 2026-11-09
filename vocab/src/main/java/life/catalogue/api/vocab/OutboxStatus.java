package life.catalogue.api.vocab;

/**
 * The state of a durable command in the outbox. A command that succeeded is deleted, so there is no final success state.
 */
public enum OutboxStatus {

  /**
   * Waiting until it is due.
   */
  PENDING,

  /**
   * Claimed by a runner and being executed.
   */
  RUNNING,

  /**
   * Failed permanently or ran out of attempts. Kept for inspection and manual retry until it is purged.
   */
  FAILED;
}
