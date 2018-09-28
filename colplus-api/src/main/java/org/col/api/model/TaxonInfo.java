package org.col.api.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TaxonInfo {
  
  private Taxon taxon;
  
  private List<String> taxonReferences;
  
  private List<Distribution> distributions;
  
  private List<VernacularName> vernacularNames;
  
  private Map<String, Reference> references = new HashMap<>();
  
  public Taxon getTaxon() {
    return taxon;
  }
  
  public void setTaxon(Taxon taxon) {
    this.taxon = taxon;
  }
  
  public List<String> getTaxonReferences() {
    return taxonReferences;
  }
  
  public void setTaxonReferences(List<String> taxonReferences) {
    this.taxonReferences = taxonReferences;
  }
  
  public List<VernacularName> getVernacularNames() {
    return vernacularNames;
  }
  
  public void setVernacularNames(List<VernacularName> vernacularNames) {
    this.vernacularNames = vernacularNames;
  }
  
  public List<Distribution> getDistributions() {
    return distributions;
  }
  
  public void setDistributions(List<Distribution> distributions) {
    this.distributions = distributions;
  }
  
  
  public Reference getReference(String id) {
    return references.getOrDefault(id, null);
  }
  
  public Map<String, Reference> getReferences() {
    return references;
  }
  
  public void setReferences(Map<String, Reference> references) {
    this.references = references;
  }
  
  public void addReference(Reference ref) {
    if (!references.containsKey(ref.getId())) {
      references.put(ref.getId(), ref);
    }
  }
  
  public void addReferences(Iterable<? extends Reference> refs) {
    for (Reference r : refs) {
      if (!references.containsKey(r.getId())) {
        references.put(r.getId(), r);
      }
    }
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TaxonInfo taxonInfo = (TaxonInfo) o;
    return Objects.equals(taxon, taxonInfo.taxon) &&
        Objects.equals(taxonReferences, taxonInfo.taxonReferences) &&
        Objects.equals(distributions, taxonInfo.distributions) &&
        Objects.equals(vernacularNames, taxonInfo.vernacularNames) &&
        Objects.equals(references, taxonInfo.references);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(taxon, taxonReferences, distributions, vernacularNames, references);
  }
}
