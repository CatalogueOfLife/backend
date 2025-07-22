package life.catalogue.api.model;

import life.catalogue.api.vocab.NomRelType;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import life.catalogue.api.vocab.Users;

import javax.annotation.Nullable;

/**
 * A nomenclatural name relation between two names pointing back in time from the nameId to the relatedNameId.
 */
public class NameRelation extends DatasetScopedEntity<Integer> implements ExtensionEntity {
  private Integer datasetKey;
  private Sector.Mode sectorMode;
  private Integer sectorKey;
  private Integer verbatimKey;
  private Integer verbatimSourceKey;
  private NomRelType type;
  private String nameId;
  private String relatedNameId;
  private String referenceId;
  private String remarks;

  @JsonIgnore
  public DSID<String> getNameKey() {
    return DSID.of(datasetKey, nameId);
  }

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

  public Integer getVerbatimSourceKey() {
    return verbatimSourceKey;
  }

  public void setVerbatimSourceKey(Integer verbatimSourceKey) {
    this.verbatimSourceKey = verbatimSourceKey;
  }

  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public NomRelType getType() {
    return type;
  }
  
  public void setType(NomRelType type) {
    this.type = type;
  }
  
  public String getNameId() {
    return nameId;
  }
  
  public void setNameId(String nameId) {
    this.nameId = nameId;
  }
  
  public String getRelatedNameId() {
    return relatedNameId;
  }
  
  public void setRelatedNameId(String key) {
    this.relatedNameId = key;
  }
  
  public String getReferenceId() {
    return referenceId;
  }
  
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

  /**
   * @return true if publishedIn or remarks are present
   */
  @JsonIgnore
  public boolean isRich() {
    return referenceId != null || remarks != null;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NameRelation)) return false;
    if (!super.equals(o)) return false;

    NameRelation that = (NameRelation) o;
    return Objects.equals(datasetKey, that.datasetKey) &&
      sectorMode == that.sectorMode &&
      Objects.equals(sectorKey, that.sectorKey) &&
      Objects.equals(verbatimKey, that.verbatimKey) &&
      Objects.equals(verbatimSourceKey, that.verbatimSourceKey) &&
      type == that.type &&
      Objects.equals(nameId, that.nameId) &&
      Objects.equals(relatedNameId, that.relatedNameId) &&
      Objects.equals(referenceId, that.referenceId) &&
      Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), datasetKey, sectorMode, sectorKey, verbatimKey, verbatimSourceKey, type, nameId, relatedNameId, referenceId, remarks);
  }

  @Override
  public String toString() {
    return "NameRelation{" + type + "dataset " + datasetKey + " sector " + sectorKey + ": " + nameId + " -> " + relatedNameId + '}';
  }
}
