package life.catalogue.api.search;

import com.google.common.base.Preconditions;
import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.vocab.Issue;

import java.util.*;

public class FacetValue<T extends Comparable<T>> implements Comparable<FacetValue<T>> {

  public static FacetValue<String> forString(Object val, int count) {
    return new FacetValue<>(val.toString(), count);
  }

  public static FacetValue<Integer> forInteger(Object val, int count) {
    // For performance reasons, integer fields may be stored as strings (keyword datatype) in Elasticsearch!
    if (val.getClass() == Integer.class) {
      return new FacetValue<>((Integer) val, count);
    }
    return new FacetValue<>(Integer.valueOf(val.toString()), count);
  }

  public static FacetValue<UUID> forUuid(Object val, int count) {
    return new FacetValue<>(UUID.fromString(val.toString()), count);
  }

  public static <U extends Enum<U>> FacetValue<U> forEnum(Class<U> enumClass, Object val, int count) {
    // Enums are always stored using their ordinal value
    int ordinal = ((Integer) val).intValue();
    return new FacetValue<>(enumClass.getEnumConstants()[ordinal], count);
  }

  /*
   * Enums that must be sorted according to their string representation rather than by their ordinal number
   */
  private static final Set<Class<? extends Enum<?>>> useEnumName = new HashSet<>(Arrays.asList(Issue.class));

  /**
   * Comparator to be used if you want to sort by facet value first, and then by document count descending. The natural order for facets is
   * by document count descending first, and then by value.
   */
  public static <U extends Comparable<U>> Comparator<FacetValue<U>> getValueComparator() {
    return (f1, f2) -> {
      int i;
      if (useEnumName.contains(f1.value.getClass())) {
        i = enumToString(f1.value).compareTo(enumToString(f2.value));
      } else {
        i = f1.value.compareTo(f2.value);
      }
      return i == 0 ? f2.count - f1.count : i;
    };
  }

  private final T value;
  private final int count;

  public FacetValue(T value, int count) {
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
    int i = other.count - count; // doc count descending
    if (i == 0) {
      if (useEnumName.contains(value.getClass())) {
        return enumToString(value).compareTo(enumToString(other.value));
      }
      return value.compareTo(other.value);
    }
    return i;
  }

  private static String enumToString(Object enum0) {
    return PermissiveEnumSerde.enumValueName((Enum<?>) enum0);
  }

}
