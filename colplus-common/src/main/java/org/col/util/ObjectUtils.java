package org.col.util;

/**
 *
 */
public class ObjectUtils {

  private ObjectUtils() {

  }

  /**
   * Returns the first of the given parameters that is not null.
   * If all given parameters are null, returns null.
   *
   * @param items
   * @param <T>
   * @return
   */
  public static <T> T coalesce(T ... items) {
    if (items != null) {
      for (T i : items) if (i != null) return i;
    }
    return null;
  }

  public static <T> T coalesce(Iterable<T> items) {
    if (items != null) {
      for (T i : items) if (i != null) return i;
    }
    return null;
  }
}
