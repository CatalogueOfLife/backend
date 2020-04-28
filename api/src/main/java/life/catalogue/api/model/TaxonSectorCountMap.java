package life.catalogue.api.model;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

import java.util.Objects;

/**
 * Sector counts by dataset key for a given taxon.
 */
public class TaxonSectorCountMap {
  /**
   * The Taxon.id
   */
  private String id;

  /**
   * Sector count map keyed on source dataset keys
   */
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

  /**
   * @return total number of sectors
   */
  public int size(){
    int total = 0;
    for (Int2IntMap.Entry entry : count.int2IntEntrySet()) {
      total =+ entry.getIntValue();
    }
    return total;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TaxonSectorCountMap that = (TaxonSectorCountMap) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(count, that.count);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(id, count);
  }

  public boolean isEmpty() {
    return count != null && size()>0;
  }
}
