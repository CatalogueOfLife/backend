package life.catalogue.api.exception;

/**
 * HTTP 429 TOO MANY REQUESTS
 */
public class TooManyRequestsException extends RuntimeException {

  public TooManyRequestsException(String message) {
    super(message);
  }
}
