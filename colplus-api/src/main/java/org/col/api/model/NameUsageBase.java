package org.col.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.col.api.vocab.Origin;
import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public abstract class NameUsageBase extends DataEntity implements NameUsage, DatasetEntity {
  
  @Nonnull
  private String id;
  @Nonnull
  private Integer datasetKey;
  private Integer sectorKey;
  private Integer verbatimKey;
  @Nonnull
  private Name name;
  @Nonnull
  private TaxonomicStatus status;
  @Nonnull
  private Origin origin;
  private String parentId;
  private String accordingTo;
  private String remarks;
  /**
   * All bibliographic reference ids for the given name usage
   */
  private List<String> referenceIds = new ArrayList<>();
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  @Override
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  @Override
  public void setDatasetKey(Integer key) {
    this.datasetKey = key;
  }
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  @Override
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  @Override
  public TaxonomicStatus getStatus() {
    return status;
  }
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    this.status = status;
  }
  
  @JsonIgnore
  public void setStatusIfNull(TaxonomicStatus status) {
    if (this.status == null) {
      this.status = Preconditions.checkNotNull(status);
    }
  }
  
  @JsonIgnore
  public boolean isProvisional() {
    return status == TaxonomicStatus.PROVISIONALLY_ACCEPTED;
  }
  
  @Override
  public Origin getOrigin() {
    return origin;
  }
  
  @Override
  public void setOrigin(Origin origin) {
    this.origin = origin;
  }
  
  public String getParentId() {
    return parentId;
  }
  
  public void setParentId(String key) {
    this.parentId = key;
  }
  
  @Override
  public String getAccordingTo() {
    return accordingTo;
  }
  
  public void setAccordingTo(String accordingTo) {
    this.accordingTo = accordingTo;
  }
  
  public void addAccordingTo(String accordingTo) {
    if (!StringUtils.isBlank(accordingTo)) {
      this.accordingTo = this.accordingTo == null ? accordingTo.trim() : this.accordingTo + " " + accordingTo.trim();
    }
  }
  
  @Override
  public String getRemarks() {
    return remarks;
  }
  
  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }
  
  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public List<String> getReferenceIds() {
    return referenceIds;
  }
  
  public void setReferenceIds(List<String> referenceIds) {
    this.referenceIds = referenceIds;
  }
  
  public SimpleName toSimpleName() {
    return new SimpleName(id, name.getScientificName(), name.getAuthorship(), name.getRank());
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameUsageBase taxon = (NameUsageBase) o;
    return Objects.equals(id, taxon.id) &&
        Objects.equals(datasetKey, taxon.datasetKey) &&
        Objects.equals(sectorKey, taxon.sectorKey) &&
        Objects.equals(verbatimKey, taxon.verbatimKey) &&
        Objects.equals(name, taxon.name) &&
        status == taxon.status &&
        origin == taxon.origin &&
        Objects.equals(parentId, taxon.parentId) &&
        Objects.equals(accordingTo, taxon.accordingTo) &&
        Objects.equals(referenceIds, taxon.referenceIds) &&
        Objects.equals(remarks, taxon.remarks);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, datasetKey, sectorKey, verbatimKey, name, status, origin, parentId, accordingTo, remarks, referenceIds);
  }
}
