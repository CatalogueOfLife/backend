package life.catalogue.assembly;

public class SyncException extends RuntimeException {
  public SyncException(String message, Throwable cause) {
    super(message, cause);
  }

  public SyncException(Throwable cause) {
    super(cause);
  }
}
