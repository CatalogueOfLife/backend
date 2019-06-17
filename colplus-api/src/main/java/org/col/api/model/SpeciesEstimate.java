package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.EstimateType;

public class SpeciesEstimate extends Decision {
  private Integer estimate;
  private EstimateType type = EstimateType.DESCRIBED_SPECIES_LIVING;
  private String referenceId;
  
  public Integer getEstimate() {
    return estimate;
  }
  
  public void setEstimate(Integer estimate) {
    this.estimate = estimate;
  }
  
  public EstimateType getType() {
    return type;
  }
  
  public void setType(EstimateType type) {
    this.type = type;
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
        type == that.type &&
        Objects.equals(referenceId, that.referenceId);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), estimate, type, referenceId);
  }
  
  @Override
  public String toString() {
    return "SpeciesEstimate{" + getKey() + ": " + estimate + " species in " + subject + '}';
  }
  
}
