package life.catalogue.api.model;

import life.catalogue.api.vocab.NomRelType;

import java.util.Objects;

/**
 * A nomenclatural name relation between two names pointing back in time from the nameId to the relatedNameId.
 */
public class NameRelation extends DatasetScopedEntity<Integer> implements VerbatimEntity {
  private Integer verbatimKey;
  private Integer datasetKey;
  private NomRelType type;
  private String nameId;
  private String relatedNameId;
  private String publishedInId;
  private String remarks;
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
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
  
  public String getPublishedInId() {
    return publishedInId;
  }
  
  public void setPublishedInId(String publishedInId) {
    this.publishedInId = publishedInId;
  }
  
  public String getRemarks() {
    return remarks;
  }
  
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  /**
   * @return true if publishedIn or remarks are present
   */
  public boolean isRich() {
    return publishedInId != null || remarks != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameRelation that = (NameRelation) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        type == that.type &&
        Objects.equals(nameId, that.nameId) &&
        Objects.equals(relatedNameId, that.relatedNameId) &&
        Objects.equals(publishedInId, that.publishedInId) &&
        Objects.equals(remarks, that.remarks);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, datasetKey, type, nameId, relatedNameId, publishedInId, remarks);
  }
}
