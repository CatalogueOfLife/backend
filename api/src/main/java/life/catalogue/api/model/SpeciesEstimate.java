package life.catalogue.api.model;

import life.catalogue.api.vocab.EstimateType;

import java.util.Objects;

public class SpeciesEstimate extends DatasetScopedEntity<Integer> {
  private SimpleName target;
  private Integer estimate;
  private EstimateType type = EstimateType.DESCRIBED_SPECIES_LIVING;
  private String referenceId;
  private String note;

  public SpeciesEstimate() {
  }

  public SpeciesEstimate(SpeciesEstimate other) {
    super(other);
    this.target = new SimpleName(other.target);
    this.estimate = other.estimate;
    this.type = other.type;
    this.referenceId = other.referenceId;
    this.note = other.note;
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
