package life.catalogue.api.model;

import java.util.Objects;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public class TaxonCountMap {
  private String id;
  private Int2IntOpenHashMap count = new Int2IntOpenHashMap();
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  public Int2IntOpenHashMap getCount() {
    return count;
  }
  
  public void setCount(Int2IntOpenHashMap count) {
    this.count = Preconditions.checkNotNull(count);
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
