package org.col.api;

import java.util.Objects;

/**
 *
 */
public class DatasetMetrics {
  private Integer key;

  public Integer getKey() {
    return key;
  }

  public void setKey(Integer key) {
    this.key = key;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DatasetMetrics that = (DatasetMetrics) o;
    return Objects.equals(key, that.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key);
  }
}
