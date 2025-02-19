package life.catalogue.api.util;

import life.catalogue.api.exception.NotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

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

  /**
   * Returns the first of the given lists that is not null and not empty. Otherwise returns null.
   */
  @SafeVarargs
  public static <T> List<T> firstNonEmptyList(List<T>... items) {
    if (items != null) {
      for (List<T> i : items)
        if (i != null && !i.isEmpty())
          return i;
    }
    return null;
  }

  /**
   * Returns the first of the given parameters that is not null. If all given parameters are null, returns null.
   *
   * @param item first concrete item
   * @param moreItems more items to check by calling their "lazy" supplier
   * @param <T>
   * @return
   */
  @SafeVarargs
  public static <T> T coalesceLazy(T item, Supplier<T>... moreItems) {
    if (item != null) {
      return item;
    }
    for (Supplier<T> i : moreItems) {
        if (i != null) {
          T val = i.get();
          if (val != null) {
            return val;
          }
        }
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

  public static <T> void copyIfNotNull(Supplier<T> getter, Consumer<T> setter) {
    T val = getter.get();
    if (val != null) {
      setter.accept(val);
    }
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
   * Call setter with value only if the value is not null.
   */
  public static <V> void setIfNotNull(Consumer<V> setter, V value) {
    if (value != null) {
      setter.accept(value);
    }
  }

  /**
   * Similar to Guavas Preconditions.checkNotNull() but raising IllegalArgumentException instead.
   */
  public static <T> T checkNotNull(@Nullable T obj) {
    if (obj == null) {
      throw new IllegalArgumentException();
    }
    return obj;
  }
  
  /**
   * Similar to Guavas Preconditions.checkNotNull() but raising IllegalArgumentException instead.
   */
  public static <T> T checkNotNull(@Nullable T obj, String message) {
    if (obj == null) {
      throw new IllegalArgumentException(message);
    }
    return obj;
  }

  /**
   * Similar to Guavas Preconditions.checkNotNull() but raising NotFoundException instead.
   */
  public static <T> T checkFound(@Nullable T obj, String message) {
    if (obj == null) {
      throw new NotFoundException(message);
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

  /**
   * Returns true if two objects are the same and not null.
   */
  public static boolean equalsNonNull(Object a, Object b) {
    return a != null && Objects.equals(a, b);
  }

  /**
   * Calls toString on the given object or returns null if it was null.
   */
  public static String toString(Object obj) {
    return obj == null ? null : obj.toString();
  }
}
