package life.catalogue.api.model;

import life.catalogue.api.vocab.EstimateType;

import java.util.Objects;

public class SpeciesEstimate extends DatasetScopedEntity<Integer> implements VerbatimEntity, Referenced, Remarkable {
  private Integer verbatimKey;
  private SimpleNameLink target;
  private Integer estimate;
  private EstimateType type = EstimateType.SPECIES_LIVING;
  private String referenceId;
  private String remarks;

  public SpeciesEstimate() {
  }

  public SpeciesEstimate(SpeciesEstimate other) {
    super(other);
    this.verbatimKey = other.verbatimKey;
    this.target = SimpleNameLink.of(other.target);
    this.estimate = other.estimate;
    this.type = other.type;
    this.referenceId = other.referenceId;
    this.remarks = other.remarks;
  }

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }

  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  public SimpleNameLink getTarget() {
    return target;
  }
  
  public void setTarget(SimpleNameLink target) {
    this.target = target;
  }

  @Override
  public String getRemarks() {
    return remarks;
  }

  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
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
        Objects.equals(verbatimKey, that.verbatimKey) &&
        Objects.equals(estimate, that.estimate) &&
        type == that.type &&
        Objects.equals(referenceId, that.referenceId) &&
        Objects.equals(remarks, that.remarks);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, target, estimate, type, referenceId, remarks);
  }
  
  @Override
  public String toString() {
    return "Estimate{" + getId() + ": " + estimate + " species in " + target + '}';
  }
  
}
