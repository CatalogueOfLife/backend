package life.catalogue.api.util;

import org.apache.commons.lang3.StringUtils;

import java.util.function.Consumer;
import java.util.function.Function;

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
   * Call setter with value only if the current value is null.
   */
  public static <V> void setIfNull(V current, Consumer<V> setter, V value) {
    if (current == null) {
      setter.accept(value);
    }
  }

  /**
   * Similar to Guavas Preconditions.checkNotNull() but raising IllegalArgumentException instead.
   */
  public static <T> T checkNotNull(T obj) {
    if (obj == null) {
      throw new IllegalArgumentException();
    }
    return obj;
  }
  
  /**
   * Similar to Guavas Preconditions.checkNotNull() but raising IllegalArgumentException instead.
   */
  public static <T> T checkNotNull(T obj, String message) {
    if (obj == null) {
      throw new IllegalArgumentException(message);
    }
    return obj;
  }

  public static <X, T> X getIfNotNull(T obj, Function<T, X> accessor) {
    if (obj == null) {
      return null;
    }
    return accessor.apply(obj);
  }

  public static boolean anyNonBlank(final String... values) {
    if (values != null) {
      for (final String val : values) {
        if (!StringUtils.isBlank(val)) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean allNonBlank(final String... values) {
    if (values != null) {
      for (final String val : values) {
        if (StringUtils.isBlank(val)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public static String toString(Object obj) {
    return obj == null ? null : obj.toString();
  }
}
