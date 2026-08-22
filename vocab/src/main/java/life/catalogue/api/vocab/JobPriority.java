package life.catalogue.api.vocab;

/**
 * Priority of background jobs. Lower ordinal = higher priority in the executor queues.
 */
public enum JobPriority {
  HIGH,
  MEDIUM,
  LOW;
}
