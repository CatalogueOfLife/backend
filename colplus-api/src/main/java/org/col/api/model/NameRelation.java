package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.NomRelType;

/**
 * A nomenclatural name relation between two names pointing back in time from the nameId to the relatedNameId.
 */
public class NameRelation extends DataEntity implements VerbatimEntity, IntKey {
  private Integer key;
  private Integer verbatimKey;
  private Integer datasetKey;
  private NomRelType type;
  private String nameId;
  private String relatedNameId;
  private String publishedInId;
  private String note;
  
  public Integer getKey() {
    return key;
  }
  
  public void setKey(Integer key) {
    this.key = key;
  }
  
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
  
  public String getNote() {
    return note;
  }
  
  public void setNote(String note) {
    this.note = note;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameRelation that = (NameRelation) o;
    return Objects.equals(key, that.key) &&
        Objects.equals(verbatimKey, that.verbatimKey) &&
        Objects.equals(datasetKey, that.datasetKey) &&
        type == that.type &&
        Objects.equals(nameId, that.nameId) &&
        Objects.equals(relatedNameId, that.relatedNameId) &&
        Objects.equals(publishedInId, that.publishedInId) &&
        Objects.equals(note, that.note);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(key, verbatimKey, datasetKey, type, nameId, relatedNameId, publishedInId, note);
  }
}
