package life.catalogue.api.exception;

public class UnavailableException extends IllegalStateException {

  public UnavailableException(String message) {
    super(message);
  }

  public static UnavailableException unavailable(String serviceName) {
    return new UnavailableException("The " + serviceName + " is currently not available");
  }
}
