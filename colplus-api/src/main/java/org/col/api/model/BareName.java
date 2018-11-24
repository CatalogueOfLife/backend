package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public class BareName implements NameUsage {
  private Name name;
  
  public BareName() {
  }
  
  public BareName(Name name) {
    this.name = name;
  }
  
  @Override
  public String getId() {
    return name.getId();
  }
  
  @Override
  public void setId(String id) {
    name.setId(id);
  }
  
  @Override
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  @Override
  public String getAccordingTo() {
    return null;
  }
  
  @Override
  public void setAccordingTo(String according) {
    // nothing, throw if new status is supposed to be non null
    if (according != null) throw new IllegalArgumentException("Bare names do not have an accordingTo");
  }
  
  @Override
  public TaxonomicStatus getStatus() {
    return null;
  }
  
  @Override
  public void setStatus(TaxonomicStatus status) {
    // nothing, throw if new status is supposed to be non null
    if (status != null) throw new IllegalArgumentException("Bare names do not have a taxonomic status");
  }
  
  @Override
  public Integer getVerbatimKey() {
    return name.getVerbatimKey();
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    name.setVerbatimKey(verbatimKey);
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BareName that = (BareName) o;
    return Objects.equals(name, that.name);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
  
}
