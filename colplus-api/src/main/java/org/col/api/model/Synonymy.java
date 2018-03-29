package org.col.api.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Objects;

/**
 * A taxonomic synonymy list, ordering names in homotypic groups.
 */
public class Synonymy {
  private final List<List<Synonym>> synonyms;

  public Synonymy() {
    this.synonyms = Lists.newArrayList();
  }

  @JsonCreator
  public Synonymy(List<List<Synonym>> synonyms) {
    this.synonyms = synonyms;
  }

  @JsonValue
  public List<List<Synonym>> getHomotypicGroups() {
    return synonyms;
  }

  public void addHomotypicGroup(List<Synonym> synonyms) {
    this.synonyms.add(synonyms);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return synonyms.isEmpty();
  }

  public int size() {
    return synonyms.stream()
        .mapToInt(List::size)
        .sum();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Synonymy synonymy = (Synonymy) o;
    return Objects.equals(synonyms, synonymy.synonyms);
  }

  @Override
  public int hashCode() {
    return Objects.hash(synonyms);
  }
}
