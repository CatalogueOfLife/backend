package org.col.api.model;

import java.util.List;
import java.util.Objects;

public class TaxonVernacularUsage extends Taxon {
  
  private List<VernacularName> vernacularNames;
  
  public List<VernacularName> getVernacularNames() {
    return vernacularNames;
  }
  
  public void setVernacularNames(List<VernacularName> vernacularNames) {
    this.vernacularNames = vernacularNames;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    TaxonVernacularUsage that = (TaxonVernacularUsage) o;
    return Objects.equals(vernacularNames, that.vernacularNames);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(super.hashCode(), vernacularNames);
  }
}
