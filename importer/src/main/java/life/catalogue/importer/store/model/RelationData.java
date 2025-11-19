package life.catalogue.importer.store.model;

import life.catalogue.api.model.*;

import java.util.Objects;

import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.SpeciesInteractionType;
import life.catalogue.api.vocab.TaxonConceptRelType;

import org.apache.commons.lang3.NotImplementedException;

public class RelationData<T extends Enum<?>> implements VerbatimEntity, Referenced {
  private String fromID;
  private String toID;
  private Integer verbatimKey;
  private T type;
  private String relatedScientificName;
  private String referenceId;
  private String remarks;

  public String getFromID() {
    return fromID;
  }

  public void setFromID(String fromID) {
    this.fromID = fromID;
  }

  public String getToID() {
    return toID;
  }

  public void setToID(String toID) {
    this.toID = toID;
  }

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public T getType() {
    return type;
  }
  
  public void setType(T type) {
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
  public Integer getDatasetKey() {
    throw new NotImplementedException("not meant for this");
  }

  @Override
  public void setDatasetKey(Integer key) {
  }

    @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RelationData)) return false;
    RelationData relation = (RelationData) o;
    return Objects.equals(verbatimKey, relation.verbatimKey) &&
      type == relation.type &&
      Objects.equals(relatedScientificName, relation.relatedScientificName) &&
      Objects.equals(referenceId, relation.referenceId) &&
      Objects.equals(remarks, relation.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(verbatimKey, type, relatedScientificName, referenceId, remarks);
  }

    /**
     * @return a collection of all name relations with name key using node ids.
     */
    public NameRelation toNameRelation() {
      NameRelation nr = new NameRelation();
      nr.setType((NomRelType) getType());
      nr.setNameId(getFromID());
      nr.setRelatedNameId(getToID());
      return copyCommon(nr);
    }

    public TaxonConceptRelation toConceptRelation() {
      TaxonConceptRelation tr = new TaxonConceptRelation();
      tr.setType((TaxonConceptRelType) getType());
      tr.setTaxonId(getFromID());
      tr.setRelatedTaxonId(getToID());
      return copyCommon(tr);
    }

    public SpeciesInteraction toSpeciesInteraction() {
      SpeciesInteraction tr = new SpeciesInteraction();
      tr.setType((SpeciesInteractionType) getType());
      tr.setTaxonId(getFromID());
      // related id could be null
      tr.setRelatedTaxonId(getToID());
      tr.setRelatedTaxonScientificName(getRelatedScientificName());
      return copyCommon(tr);
    }

    private <ENT extends DatasetScopedEntity<Integer> & VerbatimEntity & Referenced & Remarkable> ENT copyCommon(ENT obj) {
      obj.setReferenceId(getReferenceId());
      obj.setVerbatimKey(getVerbatimKey());
      obj.setRemarks(getRemarks());
      return obj;
    }
}
