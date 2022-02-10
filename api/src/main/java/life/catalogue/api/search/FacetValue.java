package life.catalogue.api.search;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.vocab.Issue;

import java.util.*;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

public class FacetValue<T extends Comparable<T>> implements Comparable<FacetValue<T>> {

  public static FacetValue<String> forString(Object val, int count) {
    return new FacetValue<>(val.toString(), count);
  }

  public static FacetValue<Integer> forInteger(Object val, int count) {
    return forInteger(val, count, null);
  }

  public static FacetValue<Integer> forInteger(Object val, int count, @Nullable Function<Integer, String> labelFunc) {
    // For performance reasons, integer fields may be stored as strings (keyword datatype) in Elasticsearch!
    Integer intVal;
    if (val.getClass() == Integer.class) {
      intVal = (Integer) val;
    } else {
      intVal = Integer.valueOf(val.toString());
    }
    if (labelFunc != null) {
      return new FacetValue<>(intVal, labelFunc.apply(intVal), count);
    }
    return new FacetValue<>(intVal, count);
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
  private final String label;
  private final int count;

  public FacetValue(T value, String label, int count) {
    Preconditions.checkNotNull(value);
    this.value = value;
    this.label = label;
    this.count = count;
  }

  public FacetValue(T value, int count) {
    this(value, null, count);
  }

  public T getValue() {
    return value;
  }

  public int getCount() {
    return count;
  }

  public String getLabel() {
    return label;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FacetValue)) return false;
    FacetValue<?> that = (FacetValue<?>) o;
    return count == that.count && Objects.equals(value, that.value) && Objects.equals(label, that.label);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, label, count);
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
