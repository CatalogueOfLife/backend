package life.catalogue.importer.neo.model;

import life.catalogue.api.model.Referenced;
import life.catalogue.api.model.VerbatimEntity;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Objects;

public class NeoRel implements VerbatimEntity, Referenced {
  private Integer verbatimKey;
  private RelType type;
  private String relatedScientificName;
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

  public String getRelatedScientificName() {
    return relatedScientificName;
  }

  public void setRelatedScientificName(String relatedScientificName) {
    this.relatedScientificName = relatedScientificName;
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
    if (!(o instanceof NeoRel)) return false;
    NeoRel neoRel = (NeoRel) o;
    return Objects.equals(verbatimKey, neoRel.verbatimKey) &&
      type == neoRel.type &&
      Objects.equals(relatedScientificName, neoRel.relatedScientificName) &&
      Objects.equals(referenceId, neoRel.referenceId) &&
      Objects.equals(remarks, neoRel.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(verbatimKey, type, relatedScientificName, referenceId, remarks);
  }

  @Override
  public Integer getDatasetKey() {
    throw new NotImplementedException("not meant for this");
  }
  
  @Override
  public void setDatasetKey(Integer key) {
  
  }
}
