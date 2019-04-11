package org.col.api.model;

import java.util.Objects;

import org.col.api.vocab.Origin;
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
    return null;
  }
  
  @Override
  public void setId(String id) {
    // Do nothing
  }
  
  @Override
  public Integer getDatasetKey() {
    return name.getDatasetKey();
  }
  
  @Override
  public void setDatasetKey(Integer key) {
    name.setDatasetKey(key);
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
  public String getRemarks() {
    return name.getRemarks();
  }
  
  @Override
  public void setRemarks(String remarks) {
    name.setRemarks(remarks);
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
  public Origin getOrigin() {
    return name.getOrigin();
  }
  
  @Override
  public void setOrigin(Origin origin) {
    name.setOrigin(origin);
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
