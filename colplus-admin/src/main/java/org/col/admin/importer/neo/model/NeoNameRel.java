package org.col.admin.importer.neo.model;

import java.util.Objects;

import org.col.api.model.VerbatimEntity;

public class NeoNameRel implements VerbatimEntity {
  private Integer verbatimKey;
  private RelType type;
  private String refId;
  private String note;
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public RelType getType() {
    return type;
  }
  
  public void setType(RelType type) {
    this.type = type;
  }
  
  public String getRefId() {
    return refId;
  }
  
  public void setRefId(String refId) {
    this.refId = refId;
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
    NeoNameRel that = (NeoNameRel) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
        type == that.type &&
        Objects.equals(refId, that.refId) &&
        Objects.equals(note, that.note);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(verbatimKey, type, refId, note);
  }
}
