package life.catalogue.api.model;

import life.catalogue.api.vocab.Area;
import life.catalogue.api.vocab.DistributionStatus;

import java.util.Objects;

/**
 *
 */
public class Distribution extends DatasetScopedEntity<Integer> implements ExtensionEntity {

  private Integer sectorKey;
  private Sector.Mode sectorMode;
  private Integer verbatimKey;
  private Area area;
  private DistributionStatus status;
  private String referenceId;
  private String remarks;

  @Override
  public Integer getSectorKey() {
    return sectorKey;
  }

  @Override
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  @Override
  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  @Override
  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public Area getArea() {
    return area;
  }
  
  public void setArea(Area area) {
    this.area = area;
  }

  public DistributionStatus getStatus() {
    return status;
  }
  
  public void setStatus(DistributionStatus status) {
    this.status = status;
  }
  
  @Override
  public String getReferenceId() {
    return referenceId;
  }
  
  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  @Override
  public String getRemarks() {
    return remarks;
  }

  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Distribution that = (Distribution) o;
    return Objects.equals(sectorKey, that.sectorKey) && sectorMode == that.sectorMode && Objects.equals(verbatimKey, that.verbatimKey) && Objects.equals(area, that.area) && status == that.status && Objects.equals(referenceId, that.referenceId) && Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, sectorMode, verbatimKey, area, status, referenceId, remarks);
  }

  @Override
  public String toString() {
    return status == null ? "Unknown" : status + " in:" + area;
  }
}
