package org.col.api.model;

import org.col.api.vocab.TaxonomicStatus;

import java.util.Objects;

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
  public TaxonomicStatus getStatus() {
    return null;
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
