package org.col.api.model;

import java.util.Objects;

import it.unimi.dsi.fastutil.ints.Int2IntMap;

public class TaxonCountMap {
  private String id;
  private Int2IntMap count;
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  public Int2IntMap getCount() {
    return count;
  }
  
  public void setCount(Int2IntMap count) {
    this.count = count;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TaxonCountMap that = (TaxonCountMap) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(count, that.count);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(id, count);
  }
}
