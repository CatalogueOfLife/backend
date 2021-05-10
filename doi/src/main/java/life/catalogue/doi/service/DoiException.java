package life.catalogue.doi.service;

/** Any exception happening during requests with agency APIs will be converted to this exception. */
public class DoiException extends Exception {

  public DoiException() {}

  public DoiException(String message) {
    super(message);
  }

  public DoiException(Throwable cause) {
    super(cause);
  }

  public DoiException(String message, Throwable cause) {
    super(message, cause);
  }
}
