package life.catalogue.api.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import life.catalogue.api.vocab.EstimateType;

public class SpeciesEstimate extends DatasetScopedEntity<Integer> {
  private SimpleName target;
  private Integer estimate;
  private EstimateType type = EstimateType.DESCRIBED_SPECIES_LIVING;
  private String referenceId;
  private String note;

  /**
   * @return the id in the old legacy property "key"
   */
  @JsonProperty("key")
  public Integer getKeyLEGACAY(){
    return getId();
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
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SpeciesEstimate that = (SpeciesEstimate) o;
    return Objects.equals(target, that.target) &&
        Objects.equals(estimate, that.estimate) &&
        type == that.type &&
        Objects.equals(referenceId, that.referenceId) &&
        Objects.equals(note, that.note);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), target, estimate, type, referenceId, note);
  }
  
  @Override
  public String toString() {
    return "Estimate{" + getId() + ": " + estimate + " species in " + target + '}';
  }
  
}
