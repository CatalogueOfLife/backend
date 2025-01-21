package life.catalogue.assembly;

public class SycnException extends RuntimeException {
  public SycnException(String message, Throwable cause) {
    super(message, cause);
  }

  public SycnException(Throwable cause) {
    super(cause);
  }
}
