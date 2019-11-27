package life.catalogue.common.lang;

public class Exceptions {

  /**
   * Wraps an exception into a runtime exceptions if needed and throws it
   */
  public static void throwRuntime(Exception e) {
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
    throw new RuntimeException(e);
  }

  public static RuntimeException asRuntimeException(Throwable t) {
    return (t instanceof RuntimeException) ? (RuntimeException) t : new RuntimeException(t);
  }

  public static void interruptIfCancelled() throws InterruptedRuntimeException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedRuntimeException();
    }
  }

  public static void interruptIfCancelled(String msg) throws InterruptedRuntimeException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedRuntimeException(msg);
    }
  }
}
