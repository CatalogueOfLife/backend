package org.col.api.search;

import java.util.Objects;

import com.google.common.base.Preconditions;

public class FacetValue<T> {

  public static FacetValue<String> forString(Object val, int count) {
    return new FacetValue<>(val.toString(), count);
  }

  public static FacetValue<Integer> forInteger(Object val, int count) {
    // See NameSearchResponseTransferTest. In theory val might could a Long or even a BigInteger, but that's very unlikely. Only if the
    // dataset key were to be a facet and it was declared to be a double precision integer in Postgress.
    Preconditions.checkArgument(val.getClass() == Integer.class, "Integer overflow");
    return new FacetValue<>((Integer) val, count);
  }

  public static <U extends Enum<U>> FacetValue<U> forEnum(Class<U> enumClass, Object val, int count) {
    // No type and array index checking here. In any ordinary workflow the arguments will come in just fine.
    int ordinal = ((Integer) val).intValue();
    return new FacetValue<>(enumClass.getEnumConstants()[ordinal], count);
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

}
