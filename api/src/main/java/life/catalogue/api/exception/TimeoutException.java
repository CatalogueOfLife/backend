package life.catalogue.api.exception;

/**
 * A server process could not finish because a timeout had occurred.
 */
public class TimeoutException extends RuntimeException {

  public TimeoutException(String message) {
    super(message);
  }

}
