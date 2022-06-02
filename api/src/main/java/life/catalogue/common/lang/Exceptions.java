package life.catalogue.common.lang;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Exceptions {

  private static Pattern QUALIFIEDCLASSSTART = Pattern.compile("^[a-z]+\\.(?:[a-z]+\\.)*[A-Z][a-zA-Z]+\\b");

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

  /**
   * Returns a concatenation of all error messages of the exception and all its causes as simple logs.
   */
  public static String simpleLogWithCauses(Throwable ex) {
    StringBuilder sb = new StringBuilder();
    appendSimpleLog(sb, ex, true, null);
    for (var c : causes(ex)) {
      appendSimpleLog(sb, c, true, "; ");
    }
    return sb.toString();
  }

  /**
   * Returns a simple log format with the simple exception class followed by the message if existing.
   */
  public static String simpleLog(Throwable ex) {
    StringBuilder sb = new StringBuilder();
    appendSimpleLog(sb, ex, false, null);
    return sb.toString();
  }

  private static void appendSimpleLog(StringBuilder sb, Throwable ex, boolean skipRuntimeExceptionWithCause, @Nullable String delimiter) {
    if (skipRuntimeExceptionWithCause && ex.getCause() != null && ex.getClass().equals(RuntimeException.class)) {
      return;
    }
    if (delimiter != null && sb.length() > 0) {
      sb.append(delimiter);
    }
    sb.append(ex.getClass().getSimpleName());
    if (ex.getMessage() != null) {
      if (!QUALIFIEDCLASSSTART.matcher(ex.getMessage()).find()) {
        sb.append(": ");
        sb.append(ex.getMessage());
      }
    }
  }

  /**
   * Returns a flat list of all causes of the given exception, but not the exception itself.
   */
  public static List<Throwable> causes(Throwable ex) {
    List<Throwable> causes = new ArrayList<>();
    while (ex.getCause() != null) {
      ex = ex.getCause();
      causes.add(ex);
    }
    return causes;
  }
}
