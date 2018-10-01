package org.col.common.lang;

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
}
