package org.col.api.model;

import java.util.Objects;

public class TaxonExtension<T extends DatasetScopedEntity<Integer>> {
  private String taxonID;
  private T obj;
  
  public String getTaxonID() {
    return taxonID;
  }
  
  public void setTaxonID(String taxonID) {
    this.taxonID = taxonID;
  }
  
  public T getObj() {
    return obj;
  }
  
  public void setObj(T obj) {
    this.obj = obj;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TaxonExtension<?> that = (TaxonExtension<?>) o;
    return Objects.equals(taxonID, that.taxonID) &&
        Objects.equals(obj, that.obj);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(taxonID, obj);
  }
}
