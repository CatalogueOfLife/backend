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
  private final List<HomotypicGroup> synonyms;

  public Synonymy() {
    this.synonyms = Lists.newArrayList();
  }

  @JsonCreator
  public Synonymy(List<HomotypicGroup> synonyms) {
    this.synonyms = synonyms;
  }

  @JsonValue
  public List<HomotypicGroup> getHomotypicGroups() {
    return synonyms;
  }

  public void addHomotypicGroup(HomotypicGroup group) {
    this.synonyms.add(group);
  }

  @JsonIgnore
  public boolean isEmpty() {
    return synonyms.isEmpty();
  }

  public int size() {
    return synonyms.stream()
        .mapToInt(HomotypicGroup::size)
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
