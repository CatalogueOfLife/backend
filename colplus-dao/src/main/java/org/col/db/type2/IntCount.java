package org.col.db.type2;

import java.util.Objects;

public class IntCount {
  private Integer key;
  private Integer count;
  
  public IntCount() {
  }
  
  public IntCount(Enum key, Integer count) {
    this(key.ordinal(), count);
  }
  
  public IntCount(Integer key, Integer count) {
    this.key = key;
    this.count = count;
  }
  
  public Integer getKey() {
    return key;
  }
  
  public void setKey(Integer key) {
    this.key = key;
  }
  
  public Integer getCount() {
    return count;
  }
  
  public void setCount(Integer count) {
    this.count = count;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IntCount intCount = (IntCount) o;
    return Objects.equals(key, intCount.key) &&
        Objects.equals(count, intCount.count);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, count);
  }
  
  @Override
  public String toString() {
    return "IntCount{" + key + "=" + count + '}';
  }
}
