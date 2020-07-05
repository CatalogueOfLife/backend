package life.catalogue.matching;

public class UnavailableException extends RuntimeException {

  public UnavailableException() {
  }

  public UnavailableException(String message) {
    super(message);
  }

  public UnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
