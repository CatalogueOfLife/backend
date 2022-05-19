package life.catalogue.common.lang;

/**
 * Custom unchecked version of InterruptedException which can be used on lambda expressions and streams.
 */
public class InterruptedRuntimeException extends RuntimeException {
  
  public InterruptedRuntimeException() {
  }

  public InterruptedRuntimeException(Exception root) {
    super(root);
  }

  public InterruptedRuntimeException(String message) {
    super(message);
  }

  public InterruptedRuntimeException(String message, Exception root) {
    super(message, root);
  }

  public InterruptedException asChecked() {
    return new InterruptedException(getMessage());
  }

}
