package life.catalogue.importer.neo.model;

import life.catalogue.api.model.Referenced;
import life.catalogue.api.model.VerbatimEntity;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Objects;

public class NeoNameRel implements VerbatimEntity, Referenced {
  private Integer verbatimKey;
  private RelType type;
  private String referenceId;
  private String remarks;
  
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
  
  public String getRemarks() {
    return remarks;
  }
  
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NeoNameRel that = (NeoNameRel) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
        type == that.type &&
        Objects.equals(referenceId, that.referenceId) &&
        Objects.equals(remarks, that.remarks);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(verbatimKey, type, referenceId, remarks);
  }
  
  @Override
  public Integer getDatasetKey() {
    throw new NotImplementedException("not meant for this");
  }
  
  @Override
  public void setDatasetKey(Integer key) {
  
  }
}
