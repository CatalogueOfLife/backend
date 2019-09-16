package org.col.importer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Splitter;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dataset archive settings specific to a dataset used by the normalizer,
 * e.g. to skip procedures that are not present/mapped in the concrete archive.
 */
public class MappingFlags {
  private static final Logger LOG = LoggerFactory.getLogger(MappingFlags.class);
  
  private boolean taxonId;
  private boolean parsedNameMapped;
  private boolean denormedClassificationMapped;
  private Set<Rank> denormedRanksMapped = new HashSet<>();
  private boolean originalNameMapped;
  private boolean acceptedNameMapped;
  private boolean parentNameMapped;
  private Map<Term, Splitter> multiValueDelimiters = new HashMap<>();
  
  /**
   * @return true if taxonID exists as a distinct property from the ID property
   * and should be used to resolve taxonomic relationships based on taxonID terms.
   */
  public boolean hasTaxonId() {
    return taxonId;
  }
  
  public void setTaxonId(boolean taxonId) {
    this.taxonId = taxonId;
  }
  
  /**
   * @return true if at least genus and specificEpithet are mapped
   */
  public boolean isParsedNameMapped() {
    return parsedNameMapped;
  }
  
  public void setParsedNameMapped(boolean parsedNameMapped) {
    this.parsedNameMapped = parsedNameMapped;
  }
  
  public boolean isDenormedClassificationMapped() {
    return denormedClassificationMapped;
  }
  
  public void setDenormedClassificationMapped(boolean denormedClassificationMapped) {
    this.denormedClassificationMapped = denormedClassificationMapped;
  }
  
  public Set<Rank> getDenormedRanksMapped() {
    return denormedRanksMapped;
  }
  
  public void setDenormedRanksMapped(Set<Rank> denormedRanksMapped) {
    this.denormedRanksMapped = denormedRanksMapped;
  }
  
  public boolean isOriginalNameMapped() {
    return originalNameMapped;
  }
  
  public void setOriginalNameMapped(boolean originalNameMapped) {
    this.originalNameMapped = originalNameMapped;
  }
  
  public boolean isAcceptedNameMapped() {
    return acceptedNameMapped;
  }
  
  public void setAcceptedNameMapped(boolean acceptedNameMapped) {
    this.acceptedNameMapped = acceptedNameMapped;
  }
  
  public boolean isParentNameMapped() {
    return parentNameMapped;
  }
  
  public void setParentNameMapped(boolean parentNameMapped) {
    this.parentNameMapped = parentNameMapped;
  }
  
  public Map<Term, Splitter> getMultiValueDelimiters() {
    return multiValueDelimiters;
  }
  
}
