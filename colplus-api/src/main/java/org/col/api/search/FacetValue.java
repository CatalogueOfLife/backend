package org.col.api.search;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.google.common.base.Preconditions;

import org.col.api.jackson.ApiModule;
import org.col.api.vocab.Issue;

public class FacetValue<T extends Comparable<T>> implements Comparable<FacetValue<T>> {

  /*
   * Enums that should be sorted according to their string representation rather than by their ordinal number
   */
  private static final Set<Class<? extends Enum<?>>> STRINGIFY = new HashSet<>(Arrays.asList(Issue.class));

  /**
   * Comparator to be used if you want to sort by facet value first, and then by document count descending. The natural order for facets is
   * by document count descending first, and then by value.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public static Comparator<FacetValue<?>> VALUE_FIRST_COMPARATOR = (f1, f2) -> {
    int i;
    if (STRINGIFY.contains(f1.getClass())) {
      i = stringify(f1.value).compareTo(stringify(f2.value));
    } else {
      i = ((Comparable) f1.value).compareTo(f2.value);
    }
    return i == 0 ? f2.count - f1.count : i;
  };

  public static FacetValue<String> forString(Object val, int count) {
    return new FacetValue<>(val.toString(), count);
  }

  /*
   * See NameSearchResponseTransferTest. Theoretically val could be a Long or even a BigInteger, but we don't try to deal with this
   * possibility. It could happen only if dataset_key were to be declared a double precision integer in Postgres.
   */
  public static FacetValue<Integer> forInteger(Object val, int count) {
    Preconditions.checkArgument(val.getClass() == Integer.class, "%s could not be cast to integer", val);
    return new FacetValue<>((Integer) val, count);
  }

  public static <U extends Enum<U>> FacetValue<U> forEnum(Class<U> enumClass, Object val, int count) {
    int ordinal = ((Integer) val).intValue();
    return new FacetValue<>(enumClass.getEnumConstants()[ordinal], count);
  }

  private final T value;
  private final int count;

  private FacetValue(T value, int count) {
    Preconditions.checkNotNull(value);
    this.value = value;
    this.count = count;
  }

  public T getValue() {
    return value;
  }

  public int getCount() {
    return count;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    FacetValue<?> other = (FacetValue<?>) obj;
    return value.equals(other.value) && count == other.count;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, count);
  }

  @Override
  public int compareTo(FacetValue<T> other) {
    int i = other.count - count; // doc count descending !
    if (i == 0) {
      if (STRINGIFY.contains(getClass())) {
        return stringify(value).compareTo(stringify(other.value));
      }
      return value.compareTo(other.value);
    }
    return i;
  }

  private static String stringify(Object enum0) {
    return ApiModule.enumValueName((Enum<?>) enum0);
  }

}
