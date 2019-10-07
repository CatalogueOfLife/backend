package org.col.importer.neo.model;

import java.util.Objects;

import org.apache.commons.lang3.NotImplementedException;
import org.col.api.model.Referenced;
import org.col.api.model.VerbatimEntity;

public class NeoNameRel implements VerbatimEntity, Referenced {
  private Integer verbatimKey;
  private RelType type;
  private String referenceId;
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
  
  @Override
  public String getReferenceId() {
    return referenceId;
  }
  
  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
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
        Objects.equals(referenceId, that.referenceId) &&
        Objects.equals(note, that.note);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(verbatimKey, type, referenceId, note);
  }
  
  @Override
  public Integer getDatasetKey() {
    throw new NotImplementedException("not meant for this");
  }
  
  @Override
  public void setDatasetKey(Integer key) {
  
  }
}
