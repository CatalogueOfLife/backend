package org.col.api.util;

/**
 *
 */
public class ObjectUtils {

  private ObjectUtils() {}

  /**
   * Returns the first of the given parameters that is not null. If all given parameters are null, returns null.
   *
   * @param items
   * @param <T>
   * @return
   */
  @SafeVarargs
  public static <T> T coalesce(T... items) {
    if (items != null) {
      for (T i : items)
        if (i != null)
          return i;
    }
    return null;
  }

  public static <T> T coalesce(Iterable<T> items) {
    if (items != null) {
      for (T i : items)
        if (i != null)
          return i;
    }
    return null;
  }
  
  /**
   * Similar to Guavas Preconditions.checkNotNull() but raising IllegalArgumentException instead.
   */
  public static <T> T checkNotNull(T obj) {
    if (obj == null) {
      throw new IllegalArgumentException();
    }
    return obj;  }
  
  /**
   * Similar to Guavas Preconditions.checkNotNull() but raising IllegalArgumentException instead.
   */
  public static <T> T checkNotNull(T obj, String message) {
    if (obj == null) {
      throw new IllegalArgumentException(message);
    }
    return obj;
  }
  
}
