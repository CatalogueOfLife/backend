package org.col.api.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.EstimateType;

public class SpeciesEstimate extends DataEntity<Integer> implements DatasetScoped {
  private Integer key;
  private Integer datasetKey; // the catalogues datasetKey
  private SimpleName target;
  private Integer estimate;
  private EstimateType type = EstimateType.DESCRIBED_SPECIES_LIVING;
  private String referenceId;
  private String note;
  
  @Override
  public Integer getKey() {
    return key;
  }
  
  @Override
  public void setKey(Integer key) {
    this.key = key;
  }
  
  @Override
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  @Override
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public SimpleName getTarget() {
    return target;
  }
  
  public void setTarget(SimpleName target) {
    this.target = target;
  }
  
  public String getNote() {
    return note;
  }
  
  public void setNote(String note) {
    this.note = note;
  }
  
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
  
  @JsonIgnore
  public DSID<String> getTargetAsDSID() {
    return target == null ? null : DSID.key(datasetKey, target.getId());
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SpeciesEstimate that = (SpeciesEstimate) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(target, that.target) &&
        Objects.equals(estimate, that.estimate) &&
        type == that.type &&
        Objects.equals(referenceId, that.referenceId) &&
        Objects.equals(note, that.note);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, datasetKey, target, estimate, type, referenceId, note);
  }
  
  @Override
  public String toString() {
    return "Estimate{" + getKey() + ": " + estimate + " species in " + target + '}';
  }
  
}
