package life.catalogue.importer.store.model;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.NomRelType;

import org.gbif.nameparser.api.Rank;

import java.util.*;


/**
 * Simple wrapper to hold all name data together for kryo persistence
 */
public class NameData implements DSID<String>, VerbatimEntity {

  public ParsedNameUsage pnu;
  public boolean homotypic = false;
  public String basionymID; // hasBasionym
  public final HashSet<String> usageIDs = new HashSet<>(); // all usages linked to this name
  public final List<RelationData<NomRelType>> relations = new ArrayList<>();

  public NameData() {
  }
  
  public NameData(ParsedNameUsage pnu) {
    this.pnu = pnu;
  }

  public NameData(Name name) {
    this.pnu = new ParsedNameUsage(name);
  }

  public Name getName() {
    return pnu.getName();
  }

  public Rank getRank() {
    return pnu == null ? null : getName().getRank();
  }

  @Override
  public Integer getVerbatimKey() {
    return pnu.getName().getVerbatimKey();
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    pnu.getName().setVerbatimKey(verbatimKey);
  }
  
  @Override
  public String getId() {
    return pnu.getName().getId();
  }
  
  @Override
  public void setId(String id) {
    pnu.getName().setId(id);
  }
  
  @Override
  public Integer getDatasetKey() {
    return pnu.getName().getDatasetKey();
  }
  
  @Override
  public void setDatasetKey(Integer key) {
    pnu.getName().setDatasetKey(key);
  }

  public List<RelationData<NomRelType>> getRelations() {
    return relations;
  }

  public void addRelation(RelationData<NomRelType> rel) {
    relations.add(rel);
  }

  public RelationData<NomRelType> getRelation(NomRelType type) {
    for (RelationData<NomRelType> rel : relations) {
      if (rel.getType() == type) {
        return rel;
      }
    }
    return null;
  }
  public List<RelationData<NomRelType>> getRelations(NomRelType type) {
    List<RelationData<NomRelType>> list = new ArrayList<>();
    for (RelationData<NomRelType> rel : relations) {
      if (rel.getType() == type) {
        list.add(rel);
      }
    }
    return list;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NameData nameData)) return false;

    return homotypic == nameData.homotypic &&
      Objects.equals(pnu, nameData.pnu) &&
      Objects.equals(usageIDs, nameData.usageIDs) &&
      relations.equals(nameData.relations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pnu, homotypic, usageIDs, relations);
  }

  @Override
  public String toString() {
    return String.format("%s [%s] %s", getId(), getRank(), pnu.getName().getLabel());
  }

  public boolean containsRelation(NomRelType nomRelType) {
    return relations.stream().anyMatch(r -> r.getType() == nomRelType);
  }
}