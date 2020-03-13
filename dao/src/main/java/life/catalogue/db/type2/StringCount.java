package life.catalogue.db.type2;

import java.util.Objects;

public class StringCount {
  private String key;
  private Integer count;
  
  public StringCount() {
  }
  
  public StringCount(Enum key, Integer count) {
    this(key.name(), count);
  }
  
  public StringCount(String key, Integer count) {
    this.key = key;
    this.count = count;
  }
  
  public String getKey() {
    return key;
  }
  
  public void setKey(String key) {
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
    StringCount that = (StringCount) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(count, that.count);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, count);
  }
  
  @Override
  public String toString() {
    return "StringCount{" + key + "=" + count + '}';
  }
}
