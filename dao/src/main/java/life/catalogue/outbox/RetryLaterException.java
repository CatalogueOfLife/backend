package life.catalogue.outbox;

/**
 * Thrown by an outbox handler when the command itself is fine, but this server cannot run it right now,
 * e.g. the sector is still syncing. The command is retried later without counting an attempt.
 */
public class RetryLaterException extends RuntimeException {

  public RetryLaterException(String message) {
    super(message);
  }
}
