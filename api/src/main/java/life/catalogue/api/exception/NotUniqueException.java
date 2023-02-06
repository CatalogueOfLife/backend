package life.catalogue.api.exception;

/**
 * Exception thrown when a unique identifier or attribute is expected but not provided.
 */
public class NotUniqueException extends IllegalArgumentException {

  public NotUniqueException() {
  }

  public NotUniqueException(String s) {
    super(s);
  }

  public NotUniqueException(String message, Throwable cause) {
    super(message, cause);
  }

  public NotUniqueException(Throwable cause) {
    super(cause);
  }
}
