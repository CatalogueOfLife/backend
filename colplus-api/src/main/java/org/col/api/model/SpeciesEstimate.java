package org.col.api.model;

import java.util.Objects;

public class SpeciesEstimate extends Decision {
  private Integer estimate;
  private String referenceId;
  
  public Integer getEstimate() {
    return estimate;
  }
  
  public void setEstimate(Integer estimate) {
    this.estimate = estimate;
  }
  
  public String getReferenceId() {
    return referenceId;
  }
  
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SpeciesEstimate that = (SpeciesEstimate) o;
    return Objects.equals(estimate, that.estimate) &&
        Objects.equals(referenceId, that.referenceId);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), estimate, referenceId);
  }
  
  @Override
  public String toString() {
    return "SpeciesEstimate{" + getKey() + ": " + estimate + " species in " + subject + '}';
  }
  
}
