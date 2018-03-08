package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Objects;

/**
 * An objective, nomenclatural synonymy listing all homotypic synonyms
 * and optionally assigning one name as the basionym.
 */
public class HomotypicGroup {
  private Name basionym;
  private List<Name> synonyms = Lists.newArrayList();

  public Name getBasionym() {
    return basionym;
  }

  public void setBasionym(Name basionym) {
    this.basionym = basionym;
  }

  public List<Name> getSynonyms() {
    return synonyms;
  }

  public void setSynonyms(List<Name> synonyms) {
    this.synonyms = synonyms;
  }

  public void addSynonym(Name synonym) {
    this.synonyms.add(synonym);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return synonyms.isEmpty() && basionym == null;
  }

  public int size() {
    return synonyms.size();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HomotypicGroup that = (HomotypicGroup) o;
    return Objects.equals(basionym, that.basionym) &&
        Objects.equals(synonyms, that.synonyms);
  }

  @Override
  public int hashCode() {
    return Objects.hash(basionym, synonyms);
  }
}
