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

  public static void interruptIfCancelled() throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException();
    }
  }

  public static void interruptIfCancelled(String msg) throws InterruptedException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedException(msg);
    }
  }

  public static void runtimeInterruptIfCancelled() throws InterruptedRuntimeException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedRuntimeException();
    }
  }

  public static void runtimeInterruptIfCancelled(String msg) throws InterruptedRuntimeException {
    if (Thread.currentThread().isInterrupted()) {
      throw new InterruptedRuntimeException(msg);
    }
  }

  /**
   * @return true if the given exception ex or any of it's root causes is an instance of exceptionClass
   */
  public static boolean containsInstanceOf(Throwable ex, final Class<? extends Exception> exceptionClass) {
    if (ex == null) {
      return false;
    }
    if (exceptionClass.isInstance(ex)) {
      return true;
    }
    return containsInstanceOf(ex.getCause(), exceptionClass);
  }

  public static boolean isRootCause(Throwable ex, Class<? extends Exception> cause) {
    return ex.getCause() != null && cause.isInstance(ex.getCause());
  }

  public static Throwable getRootCause(Throwable ex) {
    if (ex.getCause() != null && !ex.getCause().equals(ex))
      return getRootCause(ex.getCause());
    return ex;
  }

  public static String getFirstMessage(Throwable ex) {
    if (ex.getMessage() != null) {
      return ex.getMessage();
    }
    if (ex.getCause() != null) {
      return getFirstMessage(ex.getCause());
    }
    return null;
  }
}
