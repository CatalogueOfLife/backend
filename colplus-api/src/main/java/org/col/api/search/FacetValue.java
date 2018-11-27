package org.col.api.search;

public class FacetValue<T> {

  private final T value;
  private final int count;

  public FacetValue(T value, int count) {
    this.value = value;
    this.count = count;
  }

  public T getValue() {
    return value;
  }

  public int getCount() {
    return count;
  }

}
